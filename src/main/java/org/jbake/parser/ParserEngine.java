package org.jbake.parser;

import java.io.File;
import java.util.Map;

import org.apache.commons.configuration.Configuration;

public interface ParserEngine {
    /**
     * Parse given file to extract as much infos as possible
     * @param config 
     * @param file file to process
     * @param contentPath path under which content will be visible, I guess ...
     * @return a map containing all infos. Returning null indicates an error, even if an exception would be better.
     */
	public Map<String, Object> parse(Configuration config, File file, String contentPath);
}
