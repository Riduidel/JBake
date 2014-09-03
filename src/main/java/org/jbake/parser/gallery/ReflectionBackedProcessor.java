package org.jbake.parser.gallery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.metadata.Directory;
import com.drew.metadata.TagDescriptor;

public class ReflectionBackedProcessor<DirectoryType extends Directory, DescriptorType extends TagDescriptor<?>> implements
				DescriptorProcessor<DirectoryType, DescriptorType> {
	private static final String DESCRIPTION_SUFFIX = "Description";
	private static final String GETTER_PREFIX = "get";
	private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionBackedProcessor.class);

	@Override
	public Map<String, Object> process(DirectoryType directory, DescriptorType descriptor) {
		Map<String, Object> returned = new TreeMap<String, Object>();
		for (Method m : descriptor.getClass().getMethods()) {
			if (m.getParameterTypes().length == 0) {
				String name = m.getName();
				if (name.startsWith(GETTER_PREFIX) && !name.equals("getClass")) {
					String key = name.substring(GETTER_PREFIX.length());
					if (key.endsWith(DESCRIPTION_SUFFIX)) {
						key = key.substring(0, key.lastIndexOf(DESCRIPTION_SUFFIX));
					}
					try {
						Object value = callGetter(descriptor, m);
						if (value != null) {
							returned.put(directory.getName()+"."+key, value);
							LOGGER.debug("added value keyed with \"" + key + "\" having a value of type " + m.getReturnType());
						}
					} catch (Exception e) {
						if (LOGGER.isDebugEnabled()) {
							LOGGER.warn("Unable to correctly invoke " + m, e);
						} else {
							LOGGER.warn("Unable to correctly invoke " + m + " due to " + e.getMessage());
						}
						return null;
					}
				}
			}
		}
		return returned;
	}

	/**
	 * isolated getter call, allows some subclass to isolate better code by avoiding some corner cases
	 * @param descriptor
	 * @param m
	 * @return
	 * @throws Exception
	 */
	protected Object callGetter(DescriptorType descriptor, Method m) throws Exception {
		return m.invoke(descriptor);
	}

}
