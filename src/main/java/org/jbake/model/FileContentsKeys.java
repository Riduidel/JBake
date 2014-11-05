package org.jbake.model;

/**
 * Known file constant keys.
 * This interface may not yet contain all values, but i think it's a good start.
 * @author ndx
 *
 */
public interface FileContentsKeys {

	static final String PUBLISHED = "published";
	static final String DATE = "date";
	static final String PUBLISHED_DATE = "published-date";
	static final String TYPE = "type";
	static final String STATUS = "status";
	static final String URI = "uri";
	static final String FILE = "file";
	static final String TAGS = "tags";
	static final String RENDERED = "rendered";
	static final String SHA12 = "sha1";
	static final String ROOTPATH = "rootpath";
	static final String DATE_YEAR = "date_year";
	static final String DATE_MONTH = "date_month";
	static final String DATE_DAY = "date_day";
	/**
	 * Beware, this key will be associated with a joda-time interval, and not with a String !
	 * Obviously, it will only be usable using date template, as it only renders intervals pages
	 */
	static final String INTERVAL = "interval";
	static final String SUBINTERVALS = "published_subintervals";
	
}