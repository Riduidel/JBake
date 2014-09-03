package org.jbake.parser.gallery;

import java.util.Map;

import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifThumbnailDescriptor;
import com.drew.metadata.exif.ExifThumbnailDirectory;

/**
 * Wouldn't it be cool to extract the EXIF thumbnail, instead of generating one by hand ?
 * For sure it would, dude, for sure.
 * @author ndx
 *
 */
public class ExifThumbnailDescriptorProcessor extends ReflectionBackedProcessor<ExifThumbnailDirectory, ExifThumbnailDescriptor> {

	@Override
	public Map<String, Object> process(ExifThumbnailDirectory directory, ExifThumbnailDescriptor descriptor) {
		Map<String, Object> returned = super.process(directory, descriptor);
		return returned;
	}


}
