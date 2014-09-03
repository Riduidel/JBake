package org.jbake.app.gallery;

import java.io.File;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.jbake.app.ConfigUtil;
import org.jbake.app.Crawler;
import org.jbake.app.Parser;
import org.jbake.app.Crawler.Attributes;
import org.jbake.app.Crawler.Attributes.Status;
import org.jbake.parser.gallery.JPEGEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Specific tests for image parsing
 * @author ndx
 *
 */
public class ParserTest {
	
	private Parser parser;

	@Before
	public void createSampleFile() throws Exception {
		File rootPath = new File(this.getClass().getResource("/").getFile());
		Configuration config = ConfigUtil.load(rootPath);
		parser = new Parser(config,rootPath.getPath());
	}
	
	@Test
	public void parseImageWithABunchOfMetadatas() {
		URL resource = getClass().getClassLoader().getResource("content/gallery/image_with_all_metadatas.JPG");
		File image = new File(resource.getFile());
		Map<String, Object> map = parser.processFile(image);
		Assert.assertNotNull(map);
		Assert.assertEquals(Crawler.Attributes.Status.PUBLISHED, map.get(Crawler.Attributes.STATUS));
		Assert.assertEquals(JPEGEngine.DOCUMENT_TYPE, map.get(Crawler.Attributes.TYPE));
		Assert.assertNotNull(map.get(Crawler.Attributes.DATE));
		Calendar cal = Calendar.getInstance();
		cal.setTime((Date) map.get(Crawler.Attributes.DATE));
		Assert.assertEquals(3, cal.get(Calendar.MONTH));
		Assert.assertEquals(25, cal.get(Calendar.DAY_OF_MONTH));
		Assert.assertEquals(2014, cal.get(Calendar.YEAR));
	}
	
	@Test
	public void parseImageWithNoMetadatas() {
		URL resource = getClass().getClassLoader().getResource("content/gallery/image_without_metadata.jpg");
		File image = new File(resource.getFile());
		Map<String, Object> map = parser.processFile(image);
		Assert.assertNotNull(map);
		Assert.assertEquals(Crawler.Attributes.Status.PUBLISHED, map.get(Crawler.Attributes.STATUS));
		Assert.assertEquals(JPEGEngine.DOCUMENT_TYPE, map.get(Crawler.Attributes.TYPE));
		Assert.assertNotNull(map.get(Crawler.Attributes.DATE));
		Calendar current = Calendar.getInstance();
		// when no date is defined, jpegengine will put file last modified timestamp
		current.setTime(new Date(image.lastModified()));
		Calendar cal = Calendar.getInstance();
		cal.setTime((Date) map.get(Crawler.Attributes.DATE));
		Assert.assertEquals(current.get(Calendar.MONTH), cal.get(Calendar.MONTH));
		Assert.assertEquals(current.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.DAY_OF_MONTH));
		Assert.assertEquals(current.get(Calendar.YEAR), cal.get(Calendar.YEAR));
	}
}
