package org.jbake.render;

import java.io.File;

import org.apache.commons.configuration.CompositeConfiguration;
import org.jbake.app.Crawler;
import org.jbake.app.Renderer;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;

public class DatesRenderer implements RenderingTool {

	@Override
	public int render(Renderer renderer, ODatabaseDocumentTx db, File destination, File templatesPath, CompositeConfiguration config) throws RenderingException {
		if (config.getBoolean("render.dates")) {
			try {
				return renderer.renderDates(Crawler.getIntervals(db), config.getString("dates.path"));
			} catch (Exception e) {
				throw new RenderingException(e);
			}
		} else {
			return 0;
		}
	}

}
