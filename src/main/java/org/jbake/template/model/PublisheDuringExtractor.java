package org.jbake.template.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jbake.app.DocumentList;
import org.jbake.model.DocumentTypes;
import org.jbake.model.FileContentsKeys;
import org.jbake.template.ModelExtractor;
import org.joda.time.ReadableInterval;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/**
 * Collect all documents published during current period.
 * Has only meaning when used in a date template, as it requires prior calculation of the usable periods
 * @author ndx
 *
 */
public class PublisheDuringExtractor implements ModelExtractor<DocumentList> {

	@Override
	public DocumentList get(ODatabaseDocumentTx db, Map model, String key) {
		ReadableInterval interval = (ReadableInterval) model.get(FileContentsKeys.INTERVAL);
		List<ODocument> returned = new ArrayList<ODocument>();
		Date intervalStart = interval.getStart().toDate();
		Date intervalEnd = interval.getEnd().toDate();
		if(interval!=null) {
			String[] documentTypes = DocumentTypes.getDocumentTypes();
			for (String docType : documentTypes) {
				OSQLSynchQuery<ODocument> runnableQuery = new OSQLSynchQuery<ODocument>(
								"SELECT * "
								+ "FROM "+docType+" "
								+ "WHERE  status='published' "
								+ "AND date >= "+intervalStart.getTime()+" "
								+ "AND date < "+intervalEnd.getTime()+" "
								+ "ORDER BY date DESC");
				returned.addAll(db.<List<ODocument>>query(runnableQuery));
			}
		}
    	return DocumentList.wrap(returned.iterator());
	}

}
