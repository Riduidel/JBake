package org.jbake.parser.gallery;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.metadata.MetadataException;
import com.drew.metadata.jpeg.JpegDescriptor;
import com.drew.metadata.jpeg.JpegDirectory;

public class JpegDescriptorProcessor extends ReflectionBackedProcessor<JpegDirectory, JpegDescriptor>{
	private static final Logger LOGGER = LoggerFactory.getLogger(JpegDescriptorProcessor.class);

	@Override
	public Map<String, Object> process(JpegDirectory directory, JpegDescriptor descriptor) {
		Map<String, Object> returned = super.process(directory, descriptor);
		try {
			returned.put(Attributes.WIDTH, directory.getImageWidth());
		} catch (MetadataException e) {
			LOGGER.warn("Unable to get width of image ?", e);
		}
		try {
			returned.put(Attributes.HEIGHT, directory.getImageHeight());
		} catch (MetadataException e) {
			LOGGER.warn("Unable to get height of image ?", e);
		}
		return returned;
	}
}
