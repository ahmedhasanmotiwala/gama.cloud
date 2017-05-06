/*********************************************************************************************
 *
 * 'GamlAccessContents.java, in plugin ummisco.gama.ui.shared, is part of the source code of the GAMA modeling and
 * simulation platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.lang.gaml.web.ui.access;

import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

//import org.eclipse.draw2d.text.TextLayout;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import msi.gama.common.interfaces.IGamlDescription;
import msi.gama.lang.gaml.web.ui.controls.IPopupProvider;
import msi.gama.lang.gaml.web.ui.controls.Popup;
import msi.gama.lang.gaml.web.ui.resources.GamaFonts;
import msi.gama.lang.gaml.web.ui.resources.IGamaColors;
import msi.gaml.compilation.GamlIdiomsProvider;

public abstract class GamlAccessContents implements IPopupProvider {

	private static final int[][] EMPTY_INDICES = new int[0][0];

	protected Text filterText;

	protected Table table;

	// private LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

	/**
	 * A color for dulled out items created by mixing the table foreground. Will be disposed when the
	 * {@link #resourceManager} is disposed.
	 */
	// private Color grayColor;
	private TextLayout textLayout;
	protected boolean resized = false;
	private TriggerSequence keySequence;

	private Popup popup;

	/**
	 * Refreshes the contents of the quick access shell
	 *
	 * @param filter
	 *            The filter text to apply to results
	 *
	 */
	public void refresh(final String filter) {
		if (table != null) {
			final boolean filterTextEmpty = filter.length() == 0;

			final List<GamlAccessEntry>[] entries = computeMatchingEntries(filter);
			final int selectionIndex = refreshTable(entries);

			if (table.getItemCount() > 0) {
				table.setSelection(selectionIndex);
			} else if (filterTextEmpty) {
				final TableItem item = new TableItem(table, SWT.NONE);
				item.setText(0, "");
				item.setText(1, "Start typing to get results");
				item.setForeground(1, IGamaColors.GRAY_LABEL.color());
			} else {
				final TableItem item = new TableItem(table, SWT.NONE);
				item.setText(0, "No result");
				item.setForeground(0, IGamaColors.GRAY_LABEL.color());
			}

		}
	}

	private int refreshTable(final List<GamlAccessEntry>[] entries) {
		if (table.getItemCount() > entries.length && table.getItemCount() - entries.length > 20) {
			table.removeAll();
		}
		final TableItem[] items = table.getItems();
		int selectionIndex = -1;
		int index = 0;
		for (int i = 0; i < GamlIdiomsProvider.PROVIDERS.size(); i++) {
			if (entries[i] != null) {
				boolean firstEntry = true;
				for (final Iterator<GamlAccessEntry> it = entries[i].iterator(); it.hasNext();) {
					final GamlAccessEntry entry = it.next();
					entry.firstInCategory = firstEntry;
					firstEntry = false;
					if (!it.hasNext()) {
						entry.lastInCategory = true;
					}
					TableItem item;
					if (index < items.length) {
						item = items[index];
						table.clear(index);
					} else {
						item = new TableItem(table, SWT.NONE);
					}

					item.setData(entry);
					item.setText(0, entry.provider.name);
					item.setText(1, entry.element.getTitle());
					index++;
				}
			}
		}
		if (index < items.length) {
			table.remove(index, items.length - 1);
		}
		if (selectionIndex == -1) {
			selectionIndex = 0;
		}
		return selectionIndex;
	}

	/**
	 * Returns a list per provider containing matching {@link QuickAccessEntry} that should be displayed in the table
	 * given a text filter and a perfect match entry that should be given priority. The number of items returned is
	 * affected by {@link #getShowAllMatches()} and the size of the table's composite.
	 *
	 * @param filter
	 *            the string text filter to apply, possibly empty
	 * @param perfectMatch
	 *            a quick access element that should be given priority or <code>null</code>
	 * @return the array of lists (one per provider) containg the quick access entries that should be added to the
	 *         table, possibly empty
	 */
	private List<GamlAccessEntry>[] computeMatchingEntries(final String filter) {
		// collect matches in an array of lists
		@SuppressWarnings ("unchecked") final List<GamlAccessEntry>[] entries =
				new List[GamlIdiomsProvider.PROVIDERS.size()];

		final int[] indexPerProvider = new int[GamlIdiomsProvider.PROVIDERS.size()];
		int countTotal = 0;
		boolean done;
		do {
			// will be set to false if we find a provider with remaining
			// elements
			done = true;
			for (int i = 0; i < GamlIdiomsProvider.PROVIDERS.size(); i++) {
				if (entries[i] == null) {
					entries[i] = new ArrayList<GamlAccessEntry>();
					indexPerProvider[i] = 0;
				}
				int count = 0;
				final GamlIdiomsProvider<?> provider = GamlIdiomsProvider.PROVIDERS.get(i);
				if (filter.length() > 0) {
					final IGamlDescription[] sortedElements = provider.getSortedElements();
					final List<GamlAccessEntry> poorFilterMatches = new ArrayList<GamlAccessEntry>();

					int j = indexPerProvider[i];
					while (j < sortedElements.length) {
						final IGamlDescription element = sortedElements[j];
						GamlAccessEntry entry = null;
						if (filter.length() == 0) {
							if (i == 0) {
								entry = new GamlAccessEntry(element, provider, new int[0][0], new int[0][0],
										GamlAccessEntry.MATCH_PERFECT);
							}
						} else {
							final GamlAccessEntry possibleMatch = match(element, filter, provider);
							// We only have limited space so only display
							// excellent filter matches (Bug 398455)
							if (possibleMatch != null) {
								if (possibleMatch.getMatchQuality() <= GamlAccessEntry.MATCH_EXCELLENT) {
									entry = possibleMatch;
								} else {
									poorFilterMatches.add(possibleMatch);
								}
							}

						}
						if (entryEnabled(provider, entry)) {
							entries[i].add(entry);
							count++;
							countTotal++;
						}

						j++;
					}

					indexPerProvider[i] = j;
					// If there were low quality matches and there is still
					// room, add them (Bug 398455)
					for (final Iterator<GamlAccessEntry> iterator = poorFilterMatches.iterator(); iterator.hasNext();) {
						final GamlAccessEntry quickAccessEntry = iterator.next();
						entries[i].add(quickAccessEntry);
						count++;
						countTotal++;
					}
					if (j < sortedElements.length) {
						done = false;
					}
				}
			}
		} while (!done);
		return entries;
	}

	public GamlAccessEntry match(final IGamlDescription element, final String filter,
			final GamlIdiomsProvider<?> providerForMatching) {
		final String sortLabel = element.getTitle();
		int index = sortLabel.toLowerCase().indexOf(filter);
		if (index != -1) {
			final int quality = sortLabel.toLowerCase().equals(filter) ? GamlAccessEntry.MATCH_PERFECT
					: sortLabel.toLowerCase().startsWith(filter) ? GamlAccessEntry.MATCH_EXCELLENT
							: GamlAccessEntry.MATCH_GOOD;
			return new GamlAccessEntry(element, providerForMatching,
					new int[][] { { index, index + filter.length() - 1 } }, EMPTY_INDICES, quality);
		}
		final String combinedLabel = providerForMatching.name + " " + element.getTitle(); //$NON-NLS-1$
		index = combinedLabel.toLowerCase().indexOf(filter);
		if (index != -1) {
			final int lengthOfElementMatch = index + filter.length() - providerForMatching.name.length() - 1;
			if (lengthOfElementMatch > 0) { return new GamlAccessEntry(element, providerForMatching,
					new int[][] { { 0, lengthOfElementMatch - 1 } },
					new int[][] { { index, index + filter.length() - 1 } }, GamlAccessEntry.MATCH_GOOD); }
			return new GamlAccessEntry(element, providerForMatching, EMPTY_INDICES,
					new int[][] { { index, index + filter.length() - 1 } }, GamlAccessEntry.MATCH_GOOD);
		}
		return null;
	}

	/**
	 * @param provider
	 * @param entry
	 * @return <code>true</code> if the entry is enabled
	 */
	private boolean entryEnabled(final GamlIdiomsProvider<?> provider, final GamlAccessEntry entry) {
		if (entry == null) { return false; }
		return true;
	}

	private void doDispose() {
		textLayout=null;
//		if (textLayout != null && !textLayout.isDisposed()) {
//			textLayout.dispose();
//		}
	}

	protected String getId() {
		return "org.eclipse.ui.internal.QuickAccess"; //$NON-NLS-1$
	}

	protected abstract void handleElementSelected(String text, Object selectedElement);

	private void handleSelection() {
		IGamlDescription selectedElement = null;
		final String text = filterText.getText().toLowerCase();
		if (table.getSelectionCount() == 1) {
			final GamlAccessEntry entry = (GamlAccessEntry) table.getSelection()[0].getData();
			selectedElement = entry == null ? null : entry.element;
		}
		if (selectedElement != null) {
			doClose();
			handleElementSelected(text, selectedElement);
		}
	}

	/**
	 * Informs the owner of the parent composite that the quick access dialog should be closed
	 */
	protected abstract void doClose();

	/**
	 * Allows the dialog contents to interact correctly with the text box used to open it
	 * 
	 * @param filterText
	 *            text box to hook up
	 */
	public void hookFilterText(final Text filterText) {
		this.filterText = filterText;
		filterText.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(final KeyEvent e) {
				switch (e.keyCode) {
					case SWT.CR:
					case SWT.KEYPAD_CR:
						handleSelection();
						break;
					case SWT.ARROW_DOWN:
						int index = table.getSelectionIndex();
						if (index != -1 && table.getItemCount() > index + 1) {
							table.setSelection(index + 1);
						}
						break;
					case SWT.ARROW_UP:
						index = table.getSelectionIndex();
						if (index != -1 && index >= 1) {
							table.setSelection(index - 1);
						}
						break;
					case SWT.ESC:
						doClose();
						break;
				}
			}

			@Override
			public void keyReleased(final KeyEvent e) {
				// do nothing
			}
		});
		filterText.addModifyListener(e -> {
			final String text = ((Text) e.widget).getText().toLowerCase();
			refresh(text);
		});
	}

	/**
	 * Creates the table providing the contents for the quick access dialog
	 *
	 * @param composite
	 *            parent composite with {@link GridLayout}
	 * @param defaultOrientation
	 *            the window orientation to use for the table {@link SWT#RIGHT_TO_LEFT} or {@link SWT#LEFT_TO_RIGHT}
	 * @return the created table
	 */
	public Table createTable(final Composite composite, final int defaultOrientation) {
		composite.addDisposeListener(e -> doDispose());
		final Composite tableComposite = new Composite(composite, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);
		final TableColumnLayout tableColumnLayout = new TableColumnLayout();
		tableComposite.setLayout(tableColumnLayout);

		table = new Table(tableComposite, SWT.None /* SWT.SINGLE | SWT.FULL_SELECTION */);
		table.setBackground(IGamaColors.VERY_LIGHT_GRAY.color());
		table.setLinesVisible(true);
//		textLayout = new TextLayout(table.getDisplay());
//		textLayout.setOrientation(defaultOrientation);
		final Font boldFont = GamaFonts.getHelpFont();
		table.setFont(boldFont);
//		textLayout.setText("Available categories");
//		int maxProviderWidth = (int) (textLayout.getBounds().width * 1.1);
//		textLayout.setFont(boldFont);
//		for (int i = 0; i < GamlIdiomsProvider.PROVIDERS.size(); i++) {
//			final GamlIdiomsProvider<?> provider = GamlIdiomsProvider.PROVIDERS.get(i);
//			textLayout.setText(provider.name);
//			final int width = (int) (textLayout.getBounds().width * 1.1);
//			if (width > maxProviderWidth) {
//				maxProviderWidth = width;
//			}
//		}

		final TableColumn c1 = new TableColumn(table, SWT.NONE);
		final TableColumn c2 = new TableColumn(table, SWT.NONE);
//		tableColumnLayout.setColumnData(c1, new ColumnWeightData(0, maxProviderWidth));
		tableColumnLayout.setColumnData(c2, new ColumnWeightData(100, 300));

		table.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.keyCode == SWT.ARROW_UP && table.getSelectionIndex() == 0) {
					filterText.setFocus();
				} else if (e.character == SWT.ESC) {
					doClose();
				}
			}

			@Override
			public void keyReleased(final KeyEvent e) {
				// do nothing
			}
		});
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(final MouseEvent e) {

				if (table.getSelectionCount() < 1)
					return;

				if (e.button != 1)
					return;

				if (table.equals(e.getSource())) {
					final Object o = table.getItem(new Point(e.x, e.y));
					final TableItem selection = table.getSelection()[0];
					if (selection.equals(o))
						handleSelection();
				}
			}
		});

		table.addMouseListener(new MouseListener() {
			TableItem lastItem = null;

			public void mouseMove(final MouseEvent e) {
				if (table.equals(e.getSource())) {
					final Object o = table.getItem(new Point(e.x, e.y));
					if (lastItem == null ^ o == null) {
						table.setCursor(o == null ? null : table.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
					}
					if (o instanceof TableItem) {
						if (!o.equals(lastItem)) {
							lastItem = (TableItem) o;
							table.setSelection(new TableItem[] { lastItem });
							popup.display();
						}
					} else if (o == null) {
						lastItem = null;
					}
				}
			}

			@Override
			public void mouseDoubleClick(MouseEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void mouseDown(MouseEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void mouseUp(MouseEvent arg0) {
				// TODO Auto-generated method stub
				
			}
		});

		table.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				// do nothing
			}

			@Override
			public void widgetDefaultSelected(final SelectionEvent e) {
				handleSelection();
			}
		});

		popup = new Popup(this, table);

		final Listener listener = event -> {
			final GamlAccessEntry entry = (GamlAccessEntry) event.item.getData();
			if (entry != null) {
//				switch (event.type) {
//					case SWT.MeasureItem:
//						entry.measure(event, textLayout);
//						break;
//					case SWT.PaintItem:
//						entry.paint(event, textLayout);
//						break;
//					case SWT.EraseItem:
//						entry.erase(event);
//						break;
//				}
			}
		};
//		table.addListener(SWT.MeasureItem, listener);
//		table.addListener(SWT.EraseItem, listener);
//		table.addListener(SWT.PaintItem, listener);

		return table;
	}

}