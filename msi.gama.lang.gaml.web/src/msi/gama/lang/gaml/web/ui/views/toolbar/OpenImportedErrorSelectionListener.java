/*********************************************************************************************
 *
 * 'OpenImportedErrorSelectionListener.java, in plugin ummisco.gama.ui.modeling, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.lang.gaml.web.ui.views.toolbar;

import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Control;

import msi.gama.lang.gaml.web.editor.GAMAHelper;
import msi.gama.lang.gaml.web.editor.GamlEditor;
import msi.gama.lang.gaml.web.editor.GamlEditorState;
import msi.gama.lang.gaml.web.ui.menus.GamaMenu;

/**
 * The class CreateExperimentSelectionListener.
 *
 * @author drogoul
 * @since 27 août 2016
 *
 */
public class OpenImportedErrorSelectionListener implements SelectionListener {

	GamlEditor editor;
	GamlEditorState state;

	/**
	 * 
	 */
	public OpenImportedErrorSelectionListener(final GamlEditor editor, final GamlEditorState state,
			final Control toolbar) {
		this.editor = editor;
		this.state = state;
	}

	/**
	 * @see org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt.events.SelectionEvent)
	 */
	@Override
	public void widgetSelected(final SelectionEvent e) {
		final Map<String, URI> msgs = state.getImportedErrors();
		if (!msgs.isEmpty()) {
			final GamaMenu menu = new GamaMenu() {

				@Override
				protected void fillMenu() {

					for (final String s : msgs.keySet()) {
						action(s, new SelectionAdapter() {

							@Override
							public void widgetSelected(final SelectionEvent e1) {
								GAMAHelper.getGui().editModel(msgs.get(s));
							}

						}, null);
					}

				}
			};
			menu.open((Control) e.widget, e, 32);
		}
	}

	/**
	 * @see org.eclipse.swt.events.SelectionListener#widgetDefaultSelected(org.eclipse.swt.events.SelectionEvent)
	 */
	@Override
	public void widgetDefaultSelected(final SelectionEvent e) {
		widgetSelected(e);
	}

}
