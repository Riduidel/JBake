package org.jbake.parser.gallery;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.configuration.Configuration;
import org.jbake.app.Crawler;
import org.jbake.model.DocumentTypes;
import org.jbake.parser.ParserEngine;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.TagDescriptor;

/**
 * Class used to process metadata in jpeg files. This class uses Apache Commons
 * Imaging to extract as much infos as possible, then just push these data into
 * a map.
 * 
 * Notice used library allows us to process more than just jpeg, but I'm not
 * sure of what metadata other formats contains
 * 
 * @author ndx
 *
 */
public class JPEGEngine implements ParserEngine {
	/**
	 * Document type is registered at startup
	 */
	public static final String DOCUMENT_TYPE = "image";
	private static final Logger LOGGER = LoggerFactory.getLogger(JPEGEngine.class);

	private final static Map<Class<? extends Directory>, Class<? extends TagDescriptor>> usableDirectoryClasses;

	static {
		DocumentTypes.addDocumentType(DOCUMENT_TYPE);
		usableDirectoryClasses = loadDictionariesAndDescriptors();
	}

	private static Map<Class<? extends Directory>, Class<? extends TagDescriptor>> loadDictionariesAndDescriptors() {
		Map<Class<? extends Directory>, Class<? extends TagDescriptor>> returned = new HashMap<Class<? extends Directory>, Class<? extends TagDescriptor>>();
		Reflections reflections =
		// or using ConfigurationBuilder
		new Reflections(new ConfigurationBuilder().setUrls(ClasspathHelper.forPackage(Directory.class.getPackage().getName())).setScanners(
						new SubTypesScanner()));

		// now we've got a reflector, use it
		Set<Class<? extends TagDescriptor>> descriptors = reflections.getSubTypesOf(TagDescriptor.class);
		for (Class<? extends TagDescriptor> descriptorClass : descriptors) {
			Type genericSuperclass = descriptorClass.getGenericSuperclass();
			if (TagDescriptor.class.equals(descriptorClass.getSuperclass())) {
				if (genericSuperclass instanceof ParameterizedType) {
					ParameterizedType parameterized = (ParameterizedType) genericSuperclass;
					Type directoryType = parameterized.getActualTypeArguments()[0];
					if (directoryType instanceof Class) {
						Class directoryClass = (Class) directoryType;
						returned.put(directoryClass, descriptorClass);
					} else {
						LOGGER.warn(String.format("Genericaly declared directory is not a class ? It is \"%s\"", directoryType.toString())); 
					}
				} else {
					LOGGER.warn(String.format("Generic superclass is not a parameterized type ? It is \"%s\"", genericSuperclass.toString()));
				}
			} else {
				LOGGER.warn(String.format("\"%s\" is not a subclass of TagDescriptor ? Weird", genericSuperclass.toString()));
			}
		}
		return returned;
	}

	/**
	 * Parse all metadata to a map. Notice parsing the body just imply the
	 * output map will have no "body" key.
	 * 
	 * @param config
	 * @param file
	 * @param contentPath
	 * @return
	 * @see org.jbake.parser.ParserEngine#parse(org.apache.commons.configuration.Configuration,
	 *      java.io.File, java.lang.String)
	 */
	@Override
	public Map<String, Object> parse(Configuration config, File file, String contentPath) {
		Map<String, Object> returned = new TreeMap<String, Object>();
		try {
			// processing is done using metadata-extractor
			{
				Metadata metadata = ImageMetadataReader.readMetadata(file);
				for (Map.Entry<Class<? extends Directory>, Class<? extends TagDescriptor>> directoryDescriptorLoader : getDirectoriesToDescriptors().entrySet()) {
					returned.putAll(processDirectory(metadata, directoryDescriptorLoader.getKey(), directoryDescriptorLoader.getValue()));
				}
			}
			returned.put(Crawler.Attributes.TYPE, DOCUMENT_TYPE);
			// as default, all images are considered as published, because,
			// well, there is no status in EXIF/IPTC tags (as far as I know)
			returned.put(Crawler.Attributes.STATUS, Crawler.Attributes.Status.PUBLISHED);
			// date should have been pushed by ExifID0 descriptor, but if not, we use file modification time
			if(!returned.containsKey(Crawler.Attributes.DATE)) {
				returned.put(Crawler.Attributes.DATE, new Date(file.lastModified()));
			}
			return returned;
		} catch (ImageProcessingException e) {
			LOGGER.error(String.format("There was an error while processing image %s", file.getAbsolutePath()), e);
			return null;
		} catch (IOException e) {
			LOGGER.error(String.format("There was an error while processing image %s", file.getAbsolutePath()), e);
			return null;
		}
	}

	/**
	 * Get map linking directories to descriptors. TODO replace hard-coded map
	 * by use of reflections API
	 * 
	 * @return
	 */
	protected Map<Class<? extends Directory>, Class<? extends TagDescriptor>> getDirectoriesToDescriptors() {
		return usableDirectoryClasses;
	}

	/**
	 * Process given directory by doing some reflection to obtain all usable
	 * fields. We know that, in metadata extractor directory subclasses, jpeg
	 * metadata are static public int fields (yup, always)
	 * 
	 * @param metadata
	 * @param directoryClass
	 * @param descriptorClass
	 * @return
	 */
	private <Type extends Directory> Map<String, Object> processDirectory(Metadata metadata, Class<Type> directoryClass,
					Class<? extends TagDescriptor> descriptorClass) {
		try {
			Directory directory = metadata.getDirectory(directoryClass);
			if (directory == null)
				return Collections.emptyMap();
			TagDescriptor<Type> descriptor = loadDescriptor(metadata, directory, (Class<TagDescriptor<Type>>) descriptorClass);
			return DescriptorProcessors.process(directory, descriptor, descriptorClass);
		} catch (Exception e) {
			LOGGER.warn("Unable to process directory " + directoryClass.getSimpleName(), e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Create an instance of {@link TagDescriptor} linked to given directory
	 * class
	 * 
	 * @param metadata
	 * @param d
	 * @param descriptorClass
	 * @return null if no directory exists for that type
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	private <DirectoryType extends Directory, DescriptorType extends TagDescriptor<DirectoryType>> DescriptorType loadDescriptor(Metadata metadata,
					Directory d, Class<DescriptorType> descriptorClass) throws SecurityException, NoSuchMethodException, IllegalArgumentException,
					InstantiationException, IllegalAccessException, InvocationTargetException {
		// Instanciate descriptor on the fly
		Constructor c = descriptorClass.getConstructor(d.getClass());
		return (DescriptorType) c.newInstance(d);
	}
}
