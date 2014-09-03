package org.jbake.parser.gallery;

import java.util.Map;

import com.drew.metadata.Directory;
import com.drew.metadata.TagDescriptor;

public interface DescriptorProcessor<DirectoryType extends Directory, DescriptorType extends TagDescriptor> {
	public Map<String, Object> process(DirectoryType directory, DescriptorType descriptor);
}
