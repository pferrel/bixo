package bixo.fetcher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;

import bixo.utils.DomainNames;

public class RunTestFetcher {

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            LineIterator iter = FileUtils.lineIterator(new File(args[0]), "UTF-8");
            
            HashMap<String, List<String>> domainMap = new HashMap<String, List<String>>();
            
            while (iter.hasNext()) {
                String line = iter.nextLine();
                
                try {
                    URL url = new URL(line);
                    String pld = DomainNames.getPLD(url);
                    List<String> urls = domainMap.get(pld);
                    if (urls == null) {
                        urls = new ArrayList<String>();
                        domainMap.put(pld, urls);
                    }
                    
                    urls.add(url.toExternalForm());
                } catch (MalformedURLException e) {
                    System.out.println("Invalid URL in input file: " + line);
                }
            }
            
            // Now we have the URLs, so create queues for processing.
            System.out.println("Unique PLDs: " + domainMap.size());
            
            FetcherQueueMgr queueMgr = new FetcherQueueMgr();
            FetcherPolicy policy = new FetcherPolicy();

            for (String pld : domainMap.keySet()) {
                FetcherQueue queue = new FetcherQueue(pld, policy, 100);
                List<String> urls = domainMap.get(pld);
                System.out.println("Adding " + urls.size() + " URLs for " + pld);
                for (String url : urls) {
                    queue.offer(url, 0.5f);
                }
                
                queueMgr.offer(queue);
            }
            
            // We've got all of the URLs set up for crawling.
            JobConf conf = new JobConf();
            FileOutputFormat.setOutputPath(conf, new Path("build/test-data/RunTestFetcher/working"));
            FetchCollector collector = new FetchCollector(conf);
            FetcherManager threadMgr = new FetcherManager(queueMgr, new HttpClientFactory(10), collector);
            Thread t = new Thread(threadMgr);
            t.setName("Fetcher manager");
            t.start();

            // We have a bunch of pages to "fetch". Spin until we're done.
            while (!threadMgr.isDone()) {}
            t.interrupt();
        } catch (Throwable t) {
            System.err.println("Exception: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }

}