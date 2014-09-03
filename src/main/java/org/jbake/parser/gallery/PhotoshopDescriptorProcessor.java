package org.jbake.parser.gallery;

import java.util.Collections;
import java.util.Map;

import com.drew.metadata.photoshop.PhotoshopDescriptor;
import com.drew.metadata.photoshop.PhotoshopDirectory;

public class PhotoshopDescriptorProcessor extends ReflectionBackedProcessor<PhotoshopDirectory, PhotoshopDescriptor> {
	@Override
	public Map<String, Object> process(PhotoshopDirectory directory, PhotoshopDescriptor descriptor) {
		Map<String, Object> returned = Collections.emptyMap();
		try {
			descriptor.getJpegQualityString();
			returned = super.process(directory, descriptor);
		} catch(NullPointerException e)  {
			// nothing to do : photoshop info just can't be extracted, I guess
		}
		return returned;
	}
}
