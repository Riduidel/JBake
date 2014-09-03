package org.jbake.app.gallery;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.io.FileUtils;
import org.jbake.app.ConfigUtil;
import org.jbake.app.Crawler;
import org.jbake.app.DBUtil;
import org.jbake.app.Parser;
import org.jbake.app.Renderer;
import org.jbake.app.Crawler.Attributes;
import org.jbake.model.DocumentTypes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

import static org.assertj.core.api.Assertions.assertThat;

public class RendererTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File sourceFolder;
    private File destinationFolder;
    private File templateFolder;
    private CompositeConfiguration config;
    private ODatabaseDocumentTx db;

    @Before
    public void setup() throws Exception, IOException, URISyntaxException {
        URL sourceUrl = this.getClass().getResource("/");

        sourceFolder = new File(sourceUrl.getFile());
        if (!sourceFolder.exists()) {
            throw new Exception("Cannot find sample data structure!");
        }

        destinationFolder = folder.getRoot();

        templateFolder = new File(sourceFolder, "templates");
        if (!templateFolder.exists()) {
            throw new Exception("Cannot find template folder!");
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

    /**
     * Rendering implies both applying the template to the given content map, AND copying the image and associated thumbnails to the "right" directory, 
     * otherwise image won't be rendered.
     * @throws Exception
     */
    @Test
    public void renderImageWithMetadatas() throws Exception {
    	renderImage("gallery/image_with_all_metadatas");
    }

	protected void renderImage(String imagePath) throws Exception {
		Crawler crawler = new Crawler(db, sourceFolder, config);
    	crawler.crawl(new File(sourceFolder.getPath() + File.separator + "content"));
        Parser parser = new Parser(config, sourceFolder.getPath());
        Renderer renderer = new Renderer(db, destinationFolder, templateFolder, config);

		URL resource = getClass().getClassLoader().getResource("content/"+imagePath+".JPG");
		File sampleFile = new File(resource.getFile());
        Map<String, Object> content = parser.processFile(sampleFile);
        content.put(Crawler.Attributes.URI, imagePath+".html");
        content.put(Crawler.Attributes.FILE, sampleFile);
        content.put(Crawler.Attributes.ROOTPATH, "../");
        renderer.render(content);
		// There should be an HTML file generated for image
        assertThat(new File(destinationFolder, imagePath +".html")).exists();
        // And a target image file, obviously
        assertThat(new File(destinationFolder, imagePath +".JPG")).exists();
        
        // As well as some thumbnails
        assertThat(new File(destinationFolder, imagePath +".thumbnail.jpg")).exists();
        
        // content of image preview file is here not really fundamental
	}
    @Test
    public void renderImageWithoutMetadatas() throws Exception {
    	renderImage("gallery/image_without_metadata");
    }
}
