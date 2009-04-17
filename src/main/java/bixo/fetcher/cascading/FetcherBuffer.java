package bixo.fetcher.cascading;

import java.util.Iterator;

import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;

import bixo.fetcher.FetcherManager;
import bixo.fetcher.FetcherQueue;
import bixo.fetcher.FetcherQueueMgr;
import bixo.fetcher.IHttpFetcherFactory;
import bixo.fetcher.beans.FetchItem;
import bixo.fetcher.beans.FetcherPolicy;
import bixo.tuple.FetchResultTuple;
import bixo.tuple.UrlWithScoreTuple;
import cascading.flow.FlowProcess;
import cascading.flow.hadoop.HadoopFlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.BufferCall;
import cascading.operation.OperationCall;
import cascading.tuple.TupleEntry;

@SuppressWarnings("serial")
public class FetcherBuffer extends BaseOperation<String> implements cascading.operation.Buffer<String> {

    private static Logger LOG = Logger.getLogger(FetcherBuffer.class);
    private FetcherManager _fetcherMgr;
    private FetcherQueueMgr _queueMgr;
    private Thread _fetcherThread;

    private IHttpFetcherFactory _fetcherFactory;

    public FetcherBuffer(IHttpFetcherFactory factory) {
        super(FetchResultTuple.FIELDS);
        _fetcherFactory = factory;
    }

    // private FetchCollector _collector;

    @Override
    public void prepare(FlowProcess flowProcess, OperationCall<String> operationCall) {
        super.prepare(flowProcess, operationCall);
        _queueMgr = new FetcherQueueMgr();
        // TODO KKr- configure max threads in _conf?

        _fetcherMgr = new FetcherManager(_queueMgr, _fetcherFactory);

        _fetcherThread = new Thread(_fetcherMgr);
        _fetcherThread.setName("Fetcher manager");
        _fetcherThread.start();

    }

    @Override
    public void operate(FlowProcess process, BufferCall<String> buffCall) {
        Iterator<TupleEntry> values = buffCall.getArgumentsIterator();
        TupleEntry group = buffCall.getGroup();
        // TODO KKr - talk to Chris about ugly casting issue.
        Reporter reporter = ((HadoopFlowProcess) process).getReporter();

        try {
            // <key> is the PLD grouper, while each entry from <values> is a
            // FetchItem.
            String domain = group.getString(0);
            FetcherPolicy policy = new FetcherPolicy();

            // TODO KKr - base maxURLs on fetcher policy, target end of fetch
            int maxURLs = 10;

            // TODO KKr - if domain isn't already an IP address, we want to
            // covert URLs to IP addresses and segment that way, as otherwise
            // keep-alive doesn't buy us much if (as on large sites) xxx.domain
            // can go to different servers. Which means breaking it up here
            // into sorted lists, and creating a queue with the list of items
            // to be fetched (moving list logic elsewhere??)
            FetcherQueue queue = new FetcherQueue(domain, policy, maxURLs);

            while (values.hasNext()) {
                FetchItem item = new FetchItem(new UrlWithScoreTuple(values.next().getTuple()), buffCall.getOutputCollector());
                queue.offer(item);
            }

            // We're going to spin here until the queue manager decides that we
            // have available space for this next queue.
            // TODO KKr - have timeout here based on target fetch duration.
            while (!_queueMgr.offer(queue)) {
                reporter.progress();
            }
        } catch (Throwable t) {
            LOG.error("Exception during reduce", t);
        }

    }

    @Override
    public void cleanup(FlowProcess flowProcess, OperationCall<String> operationCall) {

        Reporter reporter = ((HadoopFlowProcess) flowProcess).getReporter();
        while (!_fetcherMgr.isDone()) {
            if (reporter != null) {
                reporter.progress();
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
            }

        }

        _fetcherThread.interrupt();
        
        // TODO KKr - shut down FetcherManager, so that it can do...
        // httpclient.getConnectionManager().shutdown();
    }

}