package bixo.config;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

import bixo.datum.FetchSetDatum;
import bixo.datum.ScoredUrlDatum;

@SuppressWarnings("serial")
public abstract class BaseFetchJobPolicy implements Serializable {

    public static class FetchSetInfo {
        private List<ScoredUrlDatum> _urls;
        private long _sortKey;
        private long _fetchDelay;
        private boolean _skipping;
        
        public FetchSetInfo(List<ScoredUrlDatum> urls, long sortKey, long fetchDelay, boolean skipping) {
            _urls = urls;
            _sortKey = sortKey;
            _fetchDelay = fetchDelay;
            _skipping = skipping;
        }

        public List<ScoredUrlDatum> getUrls() {
            return _urls;
        }

        public long getSortKey() {
            return _sortKey;
        }

        public long getFetchDelay() {
            return _fetchDelay;
        }

        public boolean isSkipping() {
            return _skipping;
        }
    }

    private long _defaultCrawlDelay;
    
    public void setDefaultCrawlDelay(long defaultCrawlDelay) {
        _defaultCrawlDelay = defaultCrawlDelay;
    }
    
    public long getDefaultCrawlDelay() {
        return _defaultCrawlDelay;
    }
    
    // ==============================================
    // Methods used during creation of FetchSetDatums
    // ==============================================

    abstract public void startFetchSet(String groupingKey, long crawlDelay);
    abstract public FetchSetInfo nextFetchSet(ScoredUrlDatum scoredDatum);
    abstract public FetchSetInfo endFetchSet();
    
    // ==============================================
    // Methods used during fetch of FetchSetDatums
    // ==============================================
    
    abstract public void startFetching(Iterator<FetchSetDatum> iter);
    abstract public boolean keepFetching();
    abstract public FetchSetDatum nextFetch();
    abstract public void stopFetching();

}
