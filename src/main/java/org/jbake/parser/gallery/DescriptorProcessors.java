package org.jbake.parser.gallery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.metadata.Directory;
import com.drew.metadata.TagDescriptor;
import com.drew.metadata.exif.ExifIFD0Descriptor;
import com.drew.metadata.exif.ExifThumbnailDescriptor;
import com.drew.metadata.jpeg.JpegDescriptor;
import com.drew.metadata.photoshop.PhotoshopDescriptor;

public class DescriptorProcessors {
	private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorProcessors.class);

	/**
	 * Class mapping descriptors to processors.
	 * Notice the association between {@link TagDescriptor} itself and {@link ReflectionBackedProcessor} ? well, it's the default way to find
	 * association, so please don't remove it. 
	 */
	private static Map<Class<? extends TagDescriptor>, Class<? extends DescriptorProcessor>> usableProcessors = new HashMap<Class<? extends TagDescriptor>, Class<? extends DescriptorProcessor>>() {
		{
			put(ExifIFD0Descriptor.class, ExifIFD0DescriptorProcessor.class);
			put(JpegDescriptor.class, JpegDescriptorProcessor.class);
			put(ExifThumbnailDescriptor.class, ExifThumbnailDescriptorProcessor.class);
			put(PhotoshopDescriptor.class, PhotoshopDescriptorProcessor.class);
			put(TagDescriptor.class, ReflectionBackedProcessor.class);
		}
	};

	public static Map<String, Object> process(Directory directory, TagDescriptor descriptor, Class<? extends TagDescriptor> descriptorClass) {
		if (descriptor == null)
			return Collections.emptyMap();
		DescriptorProcessor processor = null;
		if(usableProcessors.containsKey(descriptorClass))
			processor = instanciate(descriptorClass);
		if(processor==null)
			processor = instanciate(TagDescriptor.class);
		LOGGER.debug(String.format("Processing descriptor %s with processor %s", descriptorClass.getName(), processor.getClass().getName()));
		return processor.process(directory, descriptor);
	}

	protected static DescriptorProcessor instanciate(Class<? extends TagDescriptor> descriptorClass){
		try {
			return usableProcessors.get(descriptorClass).newInstance();
		} catch (InstantiationException e) {
			LOGGER.warn("Unable to instanciate processor of class "+usableProcessors.get(descriptorClass).getName(), e);
			return null;
		} catch (IllegalAccessException e) {
			LOGGER.warn("Unable to access constructor of class "+usableProcessors.get(descriptorClass).getName(), e);
			return null;
		}
	}

}
