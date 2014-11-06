package org.jbake.app;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.CompositeConfiguration;
import org.jbake.model.DocumentStatus;
import org.jbake.model.DocumentTypes;
import org.jbake.model.FileContentsKeys;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.MutableDateTime;
import org.joda.time.MutablePeriod;
import org.joda.time.ReadableInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

import static java.io.File.separator;

/**
 * Crawls a file system looking for content.
 *
 * @author Jonathan Bullock <jonbullock@gmail.com>
 */
public class Crawler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Crawler.class);

    private CompositeConfiguration config;
    private Parser parser;
    private final ODatabaseDocumentTx db;
    private String contentPath;

    /**
     * Creates new instance of Crawler.
     */
    public Crawler(ODatabaseDocumentTx db, File source, CompositeConfiguration config) {
        this.db = db;
        this.config = config;
        this.contentPath = source.getPath() + separator + config.getString(ConfigUtil.Keys.CONTENT_FOLDER);
        this.parser = new Parser(config, contentPath);
    }

    /**
     * Crawl all files and folders looking for content.
     *
     * @param path Folder to start from
     */
    public void crawl(File path) {
        File[] contents = path.listFiles(FileUtil.getFileFilter());
        if (contents != null) {
            Arrays.sort(contents);
            for (File sourceFile : contents) {
                if (sourceFile.isFile()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Processing [").append(sourceFile.getPath()).append("]... ");
                    String sha1 = buildHash(sourceFile);
                    String uri = buildURI(sourceFile);
                    boolean process = true;
                    DocumentStatus status = DocumentStatus.NEW;
                    for (String docType : DocumentTypes.getDocumentTypes()) {
                        status = findDocumentStatus(docType, uri, sha1);
                        switch (status) {
                            case UPDATED:
                                sb.append(" : modified ");
                                DBUtil.update(db, "delete from " + docType + " where sourceuri=?", uri);
                                break;
                            case IDENTICAL:
                                sb.append(" : same ");
                                process = false;
                        }
                        if (!process) {
                            break;
                        }
                    }
                    if (DocumentStatus.NEW == status) {
                        sb.append(" : new ");
                    }
                    if (process) { // new or updated
                        crawlSourceFile(sourceFile, sha1, uri);
                    }
                    LOGGER.info(sb.toString());
                }
                if (sourceFile.isDirectory()) {
                    crawl(sourceFile);
                }
            }
        }
    }

    private String buildHash(final File sourceFile) {
        String sha1;
        try {
            sha1 = FileUtil.sha1(sourceFile);
        } catch (Exception e) {
            e.printStackTrace();
            sha1 = "";
        }
        return sha1;
    }
    
    private String buildURI(final File sourceFile) {
    	String uri = FileUtil.asPath(sourceFile.getPath()).replace(FileUtil.asPath( contentPath), "");
    	// strip off leading / to enable generating non-root based sites
    	if (uri.startsWith("/")) {
    		uri = uri.substring(1, uri.length());
    	}
        return uri;
    }

    private void crawlSourceFile(final File sourceFile, final String sha1, final String uri) {
        Map<String, Object> fileContents = parser.processFile(sourceFile);
        if (fileContents != null) {
        	fileContents.put(FileContentsKeys.ROOTPATH, getPathToRoot(sourceFile));
            fileContents.put(FileContentsKeys.SHA12, sha1);
            fileContents.put(FileContentsKeys.RENDERED, false);
            if (fileContents.get(FileContentsKeys.TAGS) != null) {
                // store them as a String[]
                String[] tags = (String[]) fileContents.get(FileContentsKeys.TAGS);
                fileContents.put(FileContentsKeys.TAGS, tags);
            }
            fileContents.put(FileContentsKeys.FILE, sourceFile.getPath());
            fileContents.put(FileContentsKeys.URI, uri.substring(0, uri.lastIndexOf(".")) + FileUtil.findExtension(config, fileContents.get("type").toString()));

            String documentType = (String) fileContents.get(FileContentsKeys.TYPE);
            if (fileContents.get(FileContentsKeys.STATUS).equals(FileContentsKeys.PUBLISHED_DATE)) {
                if (fileContents.get(FileContentsKeys.DATE) != null && (fileContents.get(FileContentsKeys.DATE) instanceof Date)) {
                    if (new Date().after((Date) fileContents.get(FileContentsKeys.DATE))) {
                        fileContents.put(FileContentsKeys.STATUS, FileContentsKeys.PUBLISHED);
                    }
                }
            }
            // Do some date extraction, for later parsing
            if(fileContents.get(FileContentsKeys.DATE)!=null) {
            	DateTime date = new DateTime(fileContents.get(FileContentsKeys.DATE)); 
            	fileContents.put(FileContentsKeys.DATE_YEAR, date.year().get());
            	fileContents.put(FileContentsKeys.DATE_MONTH, date.monthOfYear().get());
            	fileContents.put(FileContentsKeys.DATE_DAY, date.dayOfMonth().get());
            }
            ODocument doc = new ODocument(documentType);
            doc.fields(fileContents);
            boolean cached = fileContents.get("cached") != null ? Boolean.valueOf((String)fileContents.get("cached")):true;
            doc.field("cached", cached);
            doc.save();
        } else {
            LOGGER.warn("{} has an invalid header, it has been ignored!", sourceFile);
        }
    }

    public String getPathToRoot(File sourceFile) {
    	File rootPath = new File(contentPath);
    	File parentPath = sourceFile.getParentFile();
    	int parentCount = 0;
    	while (!parentPath.equals(rootPath)) {
    		parentPath = parentPath.getParentFile();
    		parentCount++;
    	}
    	StringBuffer sb = new StringBuffer();
    	for (int i = 0; i < parentCount; i++) {
    		sb.append("../");
    	}
    	return sb.toString();
    }
    
    public int getDocumentCount(String docType) {
        return (int) db.countClass(docType);
    }

    public int getPostCount() {
        return getDocumentCount("post");
    }

    public int getPageCount() {
        return getDocumentCount("page");
    }

    public Set<String> getTags() {
        return getTags(db);
    }

    /**
     * Utility method allowing loading of all documents having the given tag set
     * @param db
     * @return
     */
	public Set<String> getTags(ODatabaseDocumentTx db) {
		List<ODocument> query = db.query(new OSQLSynchQuery<ODocument>("select tags from post where status='published'"));
        Set<String> result = new HashSet<String>();
        for (ODocument document : query) {
            String[] tags = DBUtil.toStringArray(document.field(FileContentsKeys.TAGS));
            Collections.addAll(result, tags);
        }
        return result;
	}

    private DocumentStatus findDocumentStatus(String docType, String uri, String sha1) {
        List<ODocument> match = DBUtil.query(db, "select sha1,rendered from " + docType + " where sourceuri=?", uri);
        if (!match.isEmpty()) {
            ODocument entries = match.get(0);
            String oldHash = entries.field(FileContentsKeys.SHA12);
            if (!(oldHash.equals(sha1)) || Boolean.FALSE.equals(entries.field(FileContentsKeys.RENDERED))) {
                return DocumentStatus.UPDATED;
            } else {
                return DocumentStatus.IDENTICAL;
            }
        } else {
            return DocumentStatus.NEW;
        }
    }

    /**
     * Get all time intervals considered of interest by JBake.
     * This list may evolve over the time (or depending upon config).
     * Currently, exported intervals are
     * <ul>
     * <li>Years</li>
     * <li>Months</li>
     * <li>Days</li>
     * </ul>
     * @return a map linking a given interval to its direct subintervals
     */
	public static Map<ReadableInterval,Set<ReadableInterval>> getIntervals(ODatabaseDocumentTx db) {
		String query = "SELECT "+FileContentsKeys.DATE+" "
						+ ", "+ FileContentsKeys.DATE+".format('yyyy') AS "+FileContentsKeys.DATE_YEAR+" "
						+ ", "+ FileContentsKeys.DATE+".format('MM') AS "+FileContentsKeys.DATE_MONTH+" "
						+ ", "+ FileContentsKeys.DATE+".format('d') AS "+FileContentsKeys.DATE_DAY+" "
						+ "FROM post "
						+ "ORDER BY "+FileContentsKeys.DATE+" ASC";
		List<ODocument> values = DBUtil.query(db, query);
		Map<ReadableInterval, Set<ReadableInterval>> returned = new HashMap<ReadableInterval, Set<ReadableInterval>>();
		// creating a magical interval supposed to contain the whole time infinity to ahve a start page
		MutableDateTime eternityStart = new MutableDateTime(); eternityStart.year().set(-10000);
		MutableDateTime eternityEnd = new MutableDateTime(); eternityEnd.year().set(100000);
		ReadableInterval eternity = new Interval(eternityStart, eternityEnd);
		returned.put(eternity, new HashSet<ReadableInterval>());
		for(ODocument dateDoc : values) {
			int year = Integer.parseInt(dateDoc.field(FileContentsKeys.DATE_YEAR).toString());
			int month = Integer.parseInt(dateDoc.field(FileContentsKeys.DATE_MONTH).toString());
			int day = Integer.parseInt(dateDoc.field(FileContentsKeys.DATE_DAY).toString());
			// Create year interval
			MutableDateTime yearStart = new MutableDateTime(0,1,1,0,0,0,0); yearStart.year().set(year);
			MutableDateTime yearEnd = yearStart.copy(); 
				yearEnd.add(new MutablePeriod(1,0,0,0,0,0,0,0));
				yearEnd.addMillis(-1);
			ReadableInterval fullYear = new Interval(yearStart, yearEnd);
			if(!returned.containsKey(fullYear)) {
				returned.put(fullYear, new HashSet<ReadableInterval>());
			}
			returned.get(eternity).add(fullYear);
			// Then month interval
			MutableDateTime monthStart = new MutableDateTime(0,1,1,0,0,0,0); 
				monthStart.year().set(year);
				monthStart.monthOfYear().set(month);
			MutableDateTime monthEnd = monthStart.copy();
				monthEnd.add(new MutablePeriod(0,1,0,0,0,0,0,0));
				monthEnd.addMillis(-1);
			ReadableInterval fullMonth = new Interval(monthStart, monthEnd);
			if(!returned.containsKey(fullMonth)) {
				returned.put(fullMonth, new HashSet<ReadableInterval>());
			}
			returned.get(fullYear).add(fullMonth);
			// And finally day interval
			MutableDateTime dayStart = new MutableDateTime(0,1,1,0,0,0,0); 
				dayStart.year().set(year);
				dayStart.monthOfYear().set(month);
				dayStart.dayOfMonth().set(day);
			MutableDateTime dayEnd = dayStart.copy();
				dayEnd.add(new MutablePeriod(0,0,0,1,0,0,0,0));
				dayEnd.addMillis(-1);
			ReadableInterval fullDay = new Interval(dayStart, dayEnd);
			if(!returned.containsKey(fullDay)) {
				returned.put(fullDay, new HashSet<ReadableInterval>());
			}
			returned.get(fullMonth).add(fullDay);
		}
		return returned;
	}
}
