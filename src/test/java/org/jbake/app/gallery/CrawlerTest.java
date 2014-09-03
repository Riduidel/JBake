package org.jbake.app.gallery;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.jbake.app.ConfigUtil;
import org.jbake.app.Crawler;
import org.jbake.app.DBUtil;
import org.jbake.app.DocumentList;
import org.jbake.parser.gallery.JPEGEngine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class CrawlerTest {
	private CompositeConfiguration config;
    private ODatabaseDocumentTx db;
    private File sourceFolder;
	
	@Before
    public void setup() throws Exception, IOException, URISyntaxException {
        URL sourceUrl = this.getClass().getResource("/");

        sourceFolder = new File(sourceUrl.getFile());
        if (!sourceFolder.exists()) {
            throw new Exception("Cannot find sample data structure!");
        }

        config = ConfigUtil.load(new File(this.getClass().getResource("/").getFile()));
        Assert.assertEquals(".html", config.getString("output.extension"));
        db = DBUtil.createDB("memory", "documents"+System.currentTimeMillis());
    }

    @After
    public void cleanup() throws InterruptedException {
        db.drop();
        db.close();
    }
	@Test
	public void crawl() throws ConfigurationException {
        Crawler crawler = new Crawler(db, sourceFolder, config);
        crawler.crawl(new File(sourceFolder.getPath() + File.separator + config.getString("content.folder")));

        Assert.assertEquals(2, crawler.getDocumentCount(JPEGEngine.DOCUMENT_TYPE));
        
        List<ODocument> results = db.query(new OSQLSynchQuery<ODocument>("select * from "+JPEGEngine.DOCUMENT_TYPE+" where status='published' order by date desc"));
        DocumentList list = DocumentList.wrap(results.iterator());
        for (Map<String,Object> content : list) {
        	assertThat(content)
        		.containsEntry(Crawler.Attributes.ROOTPATH, "../")
        		.containsKey(Crawler.Attributes.FILE);
        }
        
    }
	
}
