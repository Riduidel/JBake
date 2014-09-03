package org.jbake.template.gallery;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.io.FileUtils;
import org.imgscalr.Scalr;
import org.jbake.app.Crawler;
import org.jbake.app.Crawler.Attributes;
import org.jbake.parser.gallery.JPEGEngine;
import org.jbake.template.AbstractTemplateEngine;
import org.jbake.template.DelegatingTemplateEngine;
import org.jbake.template.RenderingException;
import org.jbake.template.TemplateEngines;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

/**
 * To render document, this template engine reuses the DelegatingTemplateEngine to locate the "real" template.
 * But, before to invoke that template, image is copied into destination folder, as well as thumbnails (which may be put ina  special folder)
 * @author ndx
 *
 */
public class ImageTemplateEngine extends AbstractTemplateEngine {

	private static final String IMAGE_THUMBNAIL_SIZE = "image.thumbnail.size";

	public static interface Attributes {
		public static final String IMAGE_NAME = "imageName";
		public static final String IMAGE_PATH = "imagePath";
		public static final String THUMBNAIL_PATH = "thumbnailPath";
		public static final String THUMBNAIL_NAME = "thumbnailName";
	}
	private static final String TEMPLATE_EXTENSION = "."+JPEGEngine.DOCUMENT_TYPE;
	private DelegatingTemplateEngine renderingEngine;

	public ImageTemplateEngine(CompositeConfiguration config, ODatabaseDocumentTx db, File destination, File templatesPath) {
		super(config, db, destination, templatesPath);
        this.renderingEngine = new DelegatingTemplateEngine(config, db, destination, templatesPath);
	}

	@Override
	public void renderDocument(Map<String, Object> model, String templateName, Writer writer) throws RenderingException {
		try {
			// Copy source file to target location
			processImage((Map<String, Object>) model.get("content"));
			// Delegate call to real template
			String realTemplateName = templateName.substring(0, templateName.lastIndexOf(TEMPLATE_EXTENSION));
			renderingEngine.renderDocument(model, realTemplateName, writer);
		} catch(Exception e) {
			throw new RenderingException("Unable to render image", e);
		}
	}

	/**
	 * Copy image under its source name to target location.
	 * This method uses {@link Attributes#FILE} and {@link Attributes#ROOTPATH} to create a relative path for image.
	 * That relative path will be used to create a destination path for image.
	 * Notice this method also handles thumbnail creation 
	 * @param model
	 * @throws IOException 
	 * @throws ImageProcessingException 
	 */
	private void processImage(Map<String, Object> model) throws IOException, ImageProcessingException {
		File source = (File) model.get(Crawler.Attributes.FILE);
		if(source==null) {
			throw new UnsupportedOperationException("we don't have file information for "+model.get(Crawler.Attributes.URI));
		}
		String outputPath = getOutputPath(model);
		File output = new File(destination, outputPath);
		FileUtils.copyFile(source, output);
		model.put(Attributes.IMAGE_PATH, outputPath);
		model.put(Attributes.IMAGE_NAME, output.getName());
		// also create a path for the thumbnail
		File thumbnail = new File(destination, outputPath.substring(0, outputPath.lastIndexOf('.'))+".thumbnail.jpg");
		createThumbnail(source, thumbnail);
		model.put(Attributes.THUMBNAIL_PATH, thumbnail);
		model.put(Attributes.THUMBNAIL_NAME, thumbnail.getName());
	}

	private void createThumbnail(File source, File thumbnail) throws ImageProcessingException, IOException {
		Metadata metadata = ImageMetadataReader.readMetadata(source);
//		if(metadata.containsDirectory(ExifThumbnailDirectory.class)) {
//			ExifThumbnailDirectory thumbnailInfo = (ExifThumbnailDirectory) metadata.getDirectory(ExifThumbnailDirectory.class);
//			if(thumbnailInfo.hasThumbnailData()) {
//				createThumnailFromExif(thumbnailInfo, source, thumbnail);
//				return;
//			}
//		}
		createThumnailByHand(source, thumbnail);
	}

	private void createThumnailFromExif(ExifThumbnailDirectory thumbnailInfo, File source, File thumbnail) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("method ImageTemplateEngine#createThumnailFromExif has not yet been implemented AT ALL");
	}

	private void createThumnailByHand(File source, File thumbnail) throws IOException {
		BufferedImage sourceImage = ImageIO.read(source);
		BufferedImage thumbnailImage = Scalr.resize(sourceImage, config.getInt(IMAGE_THUMBNAIL_SIZE, 100));
		ImageIO.write(thumbnailImage, "JPG", thumbnail);
	}

	protected String getOutputPath(Map<String, Object> model) {
		File source = (File) model.get(Crawler.Attributes.FILE);
		String pathToRoot = (String) model.get(Crawler.Attributes.ROOTPATH);
		if(pathToRoot==null) {
			throw new UnsupportedOperationException("we don't have rootpath information for "+model.get(Crawler.Attributes.URI));
		}
		String[] depth = pathToRoot.split("/");
		StringBuilder outputPath = new StringBuilder();
		if(source.isFile()) {
			outputPath.append(source.getName());
			source = source.getParentFile();
		}
		for (int i = 0; i < depth.length; i++) {
			if(outputPath.length()>0)
				outputPath.insert(0, "/");
			outputPath.insert(0, source.getName());
			source = source.getParentFile();
		}
		return outputPath.toString();
	}

}
