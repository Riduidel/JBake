package org.jbake.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.configuration.CompositeConfiguration;
import org.jbake.app.ConfigUtil.Keys;
import org.jbake.model.FileContentsKeys;
import org.jbake.model.TimeIntervals;
import org.jbake.template.DelegatingTemplateEngine;
import org.joda.time.Duration;
import org.joda.time.ReadableInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

/**
 * Render output to a file.
 *
 * @author Jonathan Bullock <jonbullock@gmail.com>
 */
public class Renderer {
    /**
     * Interface that components wishing to render multiple document should implement.
     * It allow easy iteration over a collection of items, while collecting their failures and ensuring error messages are nicely made
     * 
     * @author ndx
     * @see Renderer#renderMultiple(Collection, MultipleRenderingDelegate)
     * @param <Type>
     */
    private static interface MultipleRenderingDelegate<Type> {
    	public void renderElement(Type element) throws Exception;
    }
    
    /**
     * Config used to render one element.
     * @author ndx
     *
     */
    private static interface RenderingConfig {
    	/**
    	 * Get the path of file to output
    	 * @return
    	 */
		File getPath();

		/**
		 * Get name used in logs for this rendering config
		 * @return
		 */
		String getLoggedName();

		/**
		 * Get name of the template file used to render the supplied {@link #getModel()}
		 * @return
		 */
		String getTemplateName();

		/**
		 * Model to render
		 * @return
		 */
		Map<String, Object> getModel();
	}
	
    /**
     * A base class for different possible rendering configs
     * @author ndx
     *
     */
	private static abstract class AbstractRenderingConfig implements RenderingConfig{

		protected final File path;
		protected final String name;
		protected final String template;

		public AbstractRenderingConfig(File path, String name, String template) {
			super();
			this.path = path;
			this.name = name;
			this.template = template;
		}
		
		@Override
		public File getPath() {
			return path;
		}

		@Override
		public String getLoggedName() {
			return name;
		}

		@Override
		public String getTemplateName() {
			return template;
		}
		
	}
	/**
	 * Rendering config in which all elements are given
	 * @author ndx
	 *
	 */
	public static class ModelRenderingConfig extends AbstractRenderingConfig {
		private final Map<String, Object> model;

		public ModelRenderingConfig(File path, String name, Map<String, Object> model, String template) {
			super(path, name, template);
			this.model = model;
		}
		
		@Override
		public Map<String, Object> getModel() {
			return model;
		}
	}
	
	/**
	 * Basic implementation of rendering config
	 * @author ndx
	 *
	 */
	class DefaultRenderingConfig extends AbstractRenderingConfig {

		/**
		 * Content to render.
		 * This content will be aut-wrapped in model by {@link #getModel()}
		 */
		private final Object content;
		
		/**
		 * Build a rendering config with an output path and a name that will be used for log, template and as only data in content
		 * @param path output file path
		 * @param allInOneName name used in model, logged name and for template
		 */
		private DefaultRenderingConfig(File path, String allInOneName) {
			super(path, allInOneName, findTemplateName(allInOneName));
			this.content = Collections.singletonMap("type",allInOneName);
		}
		
		/**
		 * Build a rendering config with output path and a name that will be used for everything
		 * @param filename partial file name. Notice this name will be put in a file as a child of destination
		 * @param allInOneName name used in model, logged name and for template
		 */
		public DefaultRenderingConfig(String filename, String allInOneName) {
			super(new File(destination, filename), allInOneName, findTemplateName(allInOneName));
			this.content = Collections.singletonMap("type",allInOneName);
		}
		
		/**
		 * Constructor added due to known use of a allInOneName which is used for name, template and content
		 * @param allInOneName
		 */
		public DefaultRenderingConfig(String allInOneName) {
			this(new File(destination, allInOneName + config.getString("output.extension")), 
							allInOneName);
		}

		/**
		 * Build a model having as content the inner {@link #content}
		 * @return
		 * @see org.jbake.app.Renderer.RenderingConfig#getModel()
		 */
		@Override
		public Map<String, Object> getModel() {
	        Map<String, Object> model = new HashMap<String, Object>();
	        model.put("renderer", renderingEngine);
	        model.put("content", content);
	        return model;
		}
		
	}

    private final static Logger LOGGER = LoggerFactory.getLogger(Renderer.class);

    // TODO: should all content be made available to all templates via this class??

    private File destination;
    private CompositeConfiguration config;
    private final DelegatingTemplateEngine renderingEngine;

    /**
     * Creates a new instance of Renderer with supplied references to folders.
     *
     * @param destination   The destination folder
     * @param templatesPath The templates folder
     */
    public Renderer(ODatabaseDocumentTx db, File destination, File templatesPath, CompositeConfiguration config) {
        this.destination = destination;
        this.config = config;
        this.renderingEngine = new DelegatingTemplateEngine(config, db, destination, templatesPath);
    }

    private String findTemplateName(String docType) {
        String configKey = "template."+docType+".file";
		String returned = config.getString(configKey);
        if(returned==null) {
        	throw new NullPointerException("config do not define any template under the key "+configKey+".\nYou should change that !");
        }
        return returned;
    }

    /**
     * Render the supplied content to a file.
     *
     * @param content The content to renderDocument
     * @throws Exception
     */
    public void render(Map<String, Object> content) throws Exception {
    	String docType = (String) content.get("type");
        String outputFilename = destination.getPath() + File.separatorChar + (String) content.get("uri");
        outputFilename = outputFilename.substring(0, outputFilename.lastIndexOf("."));

        // delete existing versions if they exist in case status has changed either way
        File draftFile = new File(outputFilename + config.getString(Keys.DRAFT_SUFFIX) + FileUtil.findExtension(config, docType));
        if (draftFile.exists()) {
            draftFile.delete();
        }

        File publishedFile = new File(outputFilename + FileUtil.findExtension(config, docType));
        if (publishedFile.exists()) {
            publishedFile.delete();
        }

        if (content.get("status").equals("draft")) {
            outputFilename = outputFilename + config.getString(Keys.DRAFT_SUFFIX);
        }

        File outputFile = new File(outputFilename + FileUtil.findExtension(config,docType));
        StringBuilder sb = new StringBuilder();
        sb.append("Rendering [").append(outputFile).append("]... ");
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("content", content);
        model.put("renderer", renderingEngine);

        try {
            Writer out = createWriter(outputFile);
            renderingEngine.renderDocument(model, findTemplateName(docType), out);
            out.close();
            sb.append("done!");
            LOGGER.info(sb.toString());
        } catch (Exception e) {
            sb.append("failed!");
            LOGGER.error(sb.toString(), e);
            throw new Exception("Failed to render file. Cause: " + e.getMessage());
        }
    }

    private Writer createWriter(File file) throws IOException {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        return new OutputStreamWriter(new FileOutputStream(file), config.getString(ConfigUtil.Keys.RENDER_ENCODING));
    }
    
    private void render(RenderingConfig renderConfig) throws Exception {
        File outputFile = renderConfig.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append("Rendering ").append(renderConfig.getLoggedName()).append(" [").append(outputFile).append("]...");

        try {
            Writer out = createWriter(outputFile);
            renderingEngine.renderDocument(renderConfig.getModel(),
            				renderConfig.getTemplateName(), out);
            out.close();
            sb.append("done!");
            LOGGER.info(sb.toString());
        } catch (Exception e) {
            sb.append("failed!");
            LOGGER.error(sb.toString(), e);
            throw new Exception("Failed to render "+renderConfig.getLoggedName(), e);
        }
    }

    /**
     * Render an index file using the supplied content.
     *
     * @param indexFile The name of the output file
     * @throws Exception 
     */
    public void renderIndex(String indexFile) throws Exception {
    	render(new DefaultRenderingConfig(indexFile, "index"));
    }

    /**
     * Render an XML sitemap file using the supplied content.
     * @throws Exception 
     *
     * @see <a href="https://support.google.com/webmasters/answer/156184?hl=en&ref_topic=8476">About Sitemaps</a>
     * @see <a href="http://www.sitemaps.org/">Sitemap protocol</a>
     */
    public void renderSitemap(String sitemapFile) throws Exception {
    	render(new DefaultRenderingConfig(sitemapFile, "sitemap"));
    }

    /**
     * Render an XML feed file using the supplied content.
     *
     * @param feedFile The name of the output file
     * @throws Exception 
     */
    public void renderFeed(String feedFile) throws Exception {
    	render(new DefaultRenderingConfig(feedFile, "feed"));
    }

    /**
     * Render an archive file using the supplied content.
     *
     * @param archiveFile The name of the output file
     * @throws Exception 
     */
    public void renderArchive(String archiveFile) throws Exception {
    	render(new DefaultRenderingConfig(archiveFile, "archive"));
    }

    /**
     * Render multiple files, one for each element in collection.
     * @param elements List of elements for which we want to have documents rendered
     * @param config
     * @return
     * @throws Exception
     */
    private <Type> int renderMultiple(Collection<Type> elements, MultipleRenderingDelegate<Type> config) throws Exception {
    	int renderedCount = 0;
    	final List<String> errors = new LinkedList<String>();
        for (Type element : elements) {
            try {
            	config.renderElement(element);
                renderedCount++;
            } catch (Exception e) {
            	StringBuilder message = new StringBuilder();
            	Throwable t = e;
            	do {
            		message.append(t.getMessage()).append('\n');
            		t = t.getCause();
            	} while(t!=null);
            	errors.add(message.toString());
            }
        }
        if (!errors.isEmpty()) {
        	StringBuilder sb = new StringBuilder();
        	sb.append("Failed to render multiple elements. Cause(s):");
        	for(String error: errors) {
        		sb.append("\n" + error);
        	}
        	throw new Exception(sb.toString());
        } else {
        	return renderedCount;
        }
    }

    /**
     * Render tag files using the supplied content.
     *
     * @param tags    The content to renderDocument
     * @param tagPath The output path
     * @throws Exception 
     */
    public int renderTags(Set<String> tags, final String tagPath) throws Exception {
    	return renderMultiple(tags, new MultipleRenderingDelegate<String>() {

			@Override
			public void renderElement(String tag) throws Exception {
            	Map<String, Object> model = new HashMap<String, Object>();
            	model.put("renderer", renderingEngine);
            	model.put("tag", tag);
            	model.put("content", Collections.singletonMap("type","tag"));

            	tag = tag.trim().replace(" ", "-");
            	File path = new File(new File(destination, tagPath), tag + config.getString("output.extension"));
            	render(new ModelRenderingConfig(path,
            					"tag",
            					model,
            					findTemplateName("tag")));
			}
		});
    }

    /**
     * Render dates as hierarchies.
     * Typical use case is to obtain something like
     * dates/2014/10/28/an_index_for_this_date
     * @param toRender list of available intervals. Notice these intervals 
     * are all messed up : yearly intervals are merged with monthly and daliy ones. 
     * @param root folder for dated data.
     * @return 
     * @throws Exception 
     */
	public int renderDates(final Map<ReadableInterval, Set<ReadableInterval>> toRender, final String datesPath) throws Exception {
		return renderMultiple(toRender.keySet(), new MultipleRenderingDelegate<ReadableInterval>() {
			private final DecimalFormat format = new DecimalFormat("#00");
			public String formatFileName(ReadableInterval interval, TimeIntervals as) {
				switch (as) {
				case YEAR:
					return interval.getStart().year().getAsString();
				case MONTH:
					return format.format(interval.getStart().monthOfYear().get());
				case DAY:
					return format.format(interval.getStart().dayOfMonth().get());
				default:
					throw new UnsupportedOperationException("We don't know how to render intervals of duration "+this);
				}
			}
			
			public String formatForModel(ReadableInterval interval, TimeIntervals as) {
				switch (as) {
				case YEAR:
					return interval.getStart().year().getAsString();
				case MONTH:
					return format.format(interval.getStart().monthOfYear().get());
				case DAY:
					return format.format(interval.getStart().dayOfMonth().get());
				default:
					throw new UnsupportedOperationException("We don't know how to render intervals of duration "+this);
				}
			}
			
			/**
			 * Map linking interval date starts to the files in which they will be rendered
			 * @param intervals
			 * @return
			 */
			private Map<String, Date> toDateMap(Set<ReadableInterval> intervals) {
				Map<String, Date> returned = new TreeMap<String, Date>();
				for(ReadableInterval i : intervals) {
					TimeIntervals intervalDuration = TimeIntervals.compute(i);
					String filename = formatFileName(i, intervalDuration);
					switch(intervalDuration) {
					case YEAR: 
						// month and year work the same way : an index.html file in foolder
					case MONTH:
						filename = filename +"/index" + config.getString("output.extension");
						break;
					case DAY:
						filename = filename  + config.getString("output.extension");
					}
					returned.put(filename, i.getStart().toDate());
				}
				return returned;
			}
			
			@Override
			public void renderElement(ReadableInterval interval) throws Exception {
				File createdFile = new File(destination, datesPath);
				Map<String, Object> model = new HashMap<String, Object>();
				model.put("renderer", renderingEngine);
				model.put("content", Collections.singletonMap("type", "date"));
				model.put(FileContentsKeys.INTERVAL, interval);
				model.put(FileContentsKeys.SUBINTERVALS, toDateMap(toRender.get(interval)));
				// Maybe interval is even eternity, in whichc ase we have to
				// create the associated file
				if (interval.toDuration().isLongerThan(Duration.standardDays(365 * 10))) {
					createdFile = new File(createdFile, "index" + config.getString("output.extension"));
				} else {
					model.put(FileContentsKeys.DATE_YEAR, formatForModel(interval, TimeIntervals.YEAR));
					createdFile = new File(createdFile, formatFileName(interval, TimeIntervals.YEAR));
					if (interval.toDuration().isLongerThan(Duration.standardDays(31 * 2))) {
						// it's a fully year : it last longer than two full
						// months
						createdFile = new File(createdFile, "index" + config.getString("output.extension"));
					} else {
						createdFile = new File(createdFile, formatFileName(interval, TimeIntervals.MONTH));
						model.put(FileContentsKeys.DATE_MONTH, formatForModel(interval, TimeIntervals.MONTH));
						if (interval.toDuration().isLongerThan(Duration.standardDays(7 * 2))) {
							// It's a full month : it last longer than two full
							// weeks
							createdFile = new File(createdFile, "index" + config.getString("output.extension"));
						} else {
							createdFile = new File(createdFile, formatFileName(interval, TimeIntervals.DAY) + config.getString("output.extension"));
							model.put(FileContentsKeys.DATE_DAY, formatForModel(interval, TimeIntervals.DAY));
						}
					}
				}
				render(new ModelRenderingConfig(createdFile,
								"date",
								model,
								findTemplateName("date")));
			}
		});
	}
    
    /**
     * Builds simple map of values, which are exposed when rendering index/archive/sitemap/feed/tags.
     * 
     * @param type
     * @return
     */
    private Map<String, Object> buildSimpleModel(String type) {
    	Map<String, Object> content = new HashMap<String, Object>();
    	content.put("type", type);
    	content.put("rootpath", "");
    	// add any more keys here that need to have a default value to prevent need to perform null check in templates
    	return content;
    }
}
