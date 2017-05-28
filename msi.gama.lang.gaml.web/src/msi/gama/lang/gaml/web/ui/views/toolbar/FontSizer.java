/*********************************************************************************************
 *
 * 'FontSizer.java, in plugin ummisco.gama.ui.shared, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.lang.gaml.web.ui.views.toolbar;

import org.eclipse.rap.rwt.RWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.GestureEvent;
import org.eclipse.swt.events.GestureListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Control;

import msi.gama.lang.gaml.web.ui.utils.WorkbenchHelper;

/**
 * Class FontSizer.
 *
 * @author drogoul
 * @since 9 févr. 2015
 *
 */
public class FontSizer {

	IToolbarDecoratedView.Sizable view;
	Font currentFont;

	private final GestureListener gl = new GestureListener() {

		@Override
		public void gesture(final GestureEvent ge) {
			if (ge.detail == SWT.GESTURE_MAGNIFY) {
				changeFontSize((int) (2 * Math.signum(ge.magnification - 1.0)));
			}
		}
	};

	public FontSizer(final IToolbarDecoratedView.Sizable view) {
		// We add a control listener to the toolbar in order to install the
		// gesture once the control to resize have been created.
		this.view = view;
	}

	private void changeFontSize(final int delta) {
		final Control c = view.getSizableFontControl();
		if (c != null) {
			final FontData data = c.getFont().getFontData()[0];
			data.setHeight(data.getHeight() + delta);
			if (data.getHeight() < 6 || data.getHeight() > 256) {
				return;
			}
			final Font oldFont = currentFont;

			final String uid=RWT.getUISession().getAttribute("user").toString();
			currentFont = new Font(WorkbenchHelper.getDisplay(uid), data);
			c.setFont(currentFont);
			if (oldFont != null && !oldFont.isDisposed()) {
				oldFont.dispose();
			}
		}
	}

	/**
	 * @param tb
	 */
	public void install(final GamaToolbar2 tb) {

		// We add a control listener to the toolbar in order to install the
		// gesture once the control to resize have been created.
		tb.addControlListener(new ControlAdapter() {

			@Override
			public void controlResized(final ControlEvent e) {
				final Control c = view.getSizableFontControl();
				if (c != null) {
					c.addGestureListener(gl);
					// once installed the listener removes itself from the
					// toolbar
					tb.removeControlListener(this);
				}
			}

		});
		tb.button("console.increase2", "Increase font size", "Increase font size", new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				changeFontSize(2);
			}

		}, SWT.RIGHT);
		tb.button("console.decrease2", "Decrease font size", "Decrease font size", new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				changeFontSize(-2);
			}
		}, SWT.RIGHT);

		tb.sep(16, SWT.RIGHT);

	}

}
