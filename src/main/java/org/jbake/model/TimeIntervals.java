package org.jbake.model;

import java.io.File;

import org.joda.time.Duration;
import org.joda.time.ReadableInterval;

/**
 * List intervals currently handled by JBake
 * @author ndx
 *
 */
public enum TimeIntervals {
	YEAR,
	MONTH,
	DAY;

	public static TimeIntervals compute(ReadableInterval interval) {
    	if(interval.toDuration().isLongerThan(Duration.standardDays(31*2))) {
    		return YEAR;
    	} else {
        	if(interval.toDuration().isLongerThan(Duration.standardDays(7*2))) {
        		return MONTH;
        	} else {
        		return DAY;
        	}
    	}
	}
}
