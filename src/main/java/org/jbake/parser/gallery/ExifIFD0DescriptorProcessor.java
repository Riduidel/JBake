package org.jbake.parser.gallery;

import java.util.Date;
import java.util.Map;

import org.jbake.app.Crawler;

import com.drew.metadata.exif.ExifIFD0Descriptor;
import com.drew.metadata.exif.ExifIFD0Directory;

public class ExifIFD0DescriptorProcessor extends ReflectionBackedProcessor<ExifIFD0Directory, ExifIFD0Descriptor> {
	@Override
	public Map<String, Object> process(ExifIFD0Directory directory, ExifIFD0Descriptor descriptor) {
		Map<String, Object> returned = super.process(directory, descriptor);
		Date exifDateTime = directory.getDate(ExifIFD0Directory.TAG_DATETIME);
		if(exifDateTime!=null)
			returned.put(Crawler.Attributes.DATE, exifDateTime);
		return returned;
	}
}
