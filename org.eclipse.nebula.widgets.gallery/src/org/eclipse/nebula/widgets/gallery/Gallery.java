/*******************************************************************************
 * Copyright (c) 2006-2007 Nicolas Richeton.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors :
 *    Nicolas Richeton (nicolas.richeton@gmail.com) - initial API and implementation
 *    Tom Schindl      (tom.schindl@bestsolution.at) - fix for bug 174933
 *******************************************************************************/

package org.eclipse.nebula.widgets.gallery;

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.TypedListener;

/**
 * SWT Widget that displays a picture gallery<br/> see
 * http://nicolas.richeton.free.fr/swtgallery<br/>This class must be compatible
 * with jdk-1.4
 * 
 * <p>
 * NOTE: THIS WIDGET AND ITS API ARE STILL UNDER DEVELOPMENT. THIS IS A
 * PRE-RELEASE ALPHA VERSION. USERS SHOULD EXPECT API CHANGES IN FUTURE
 * VERSIONS.
 * </p>
 * 
 * @author Nicolas Richeton (nicolas.richeton@gmail.com)
 */
public class Gallery extends Canvas {

	protected static boolean DEBUG = false;

	GalleryItem[] items = null;

	private GalleryItem[] selection = null;

	private int[] selectionIndices = null;

	/**
	 * Virtual mode flag.
	 */
	boolean virtual = false;

	boolean vertical = true;

	/**
	 * Multi selection flag
	 */
	boolean multi = false;

	int itemCount = 0;

	int interpolation = SWT.HIGH;

	int antialias = SWT.ON;

	// Internals

	private int gHeight = 0;

	private int gWidth = 0;

	int lastIndexOf = 0;

	private GalleryItem lastSingleClick = null;

	private Color backgroundColor;

	protected int translate = 0;

	AbstractGalleryItemRenderer itemRenderer;

	AbstractGalleryGroupRenderer groupRenderer;

	/**
	 * Return the number of rooot-level items in the receiver.
	 * 
	 * @return
	 */
	public int getItemCount() {
		checkWidget();
		if (virtual)
			return itemCount;

		if (items == null)
			return 0;

		return items.length;
	}

	/**
	 * Sets the number of root-level items contained in the receiver. Only work
	 * in VIRTUAL mode.
	 * 
	 * @return
	 */
	public void setItemCount(int count) {
		checkWidget();

		if (DEBUG)
			System.out.println("setCount" + count);

		if (virtual) {
			if (count == 0) {
				// No items
				items = null;
			} else {
				// At least one item, create a new array and copy data from the
				// old one.
				GalleryItem[] newItems = new GalleryItem[count];
				if (items != null) {
					System.arraycopy(items, 0, newItems, 0, Math.min(count, items.length));
				}
				items = newItems;
			}
			this.itemCount = count;

			updateStructuralValues(false);
			this.updateScrollBarsProperties();
			redraw();
		}
	}

	/**
	 * Get current item renderer
	 * 
	 * @return
	 */
	public AbstractGalleryItemRenderer getItemRenderer() {
		checkWidget();
		return itemRenderer;
	}

	/**
	 * Set item receiver. Usually, this does not trigger gallery update. redraw
	 * must be called right after setGroupRenderer to reflect this change.
	 * 
	 * @param itemRenderer
	 */
	public void setItemRenderer(AbstractGalleryItemRenderer itemRenderer) {
		checkWidget();
		this.itemRenderer = itemRenderer;

		if (itemRenderer != null)
			itemRenderer.setGallery(this);
	}

	/**
	 * Add selection listener
	 * 
	 * @param listener
	 */
	public void addSelectionListener(SelectionListener listener) {
		checkWidget();
		addListener(SWT.Selection, new TypedListener(listener));
	}

	/**
	 * Remove selection listener
	 * 
	 * @param listener
	 */
	public void removeSelectionListener(SelectionListener listener) {
		checkWidget();
		removeListener(SWT.Selection, listener);
	}

	/**
	 * Send SWT.PaintItem for one item.
	 * 
	 * @param item
	 * @param index
	 * @param gc
	 * @param x
	 * @param y
	 */
	protected void sendPaintItemEvent(Item item, int index, GC gc, int x, int y, int width, int height) {

		Event e = new Event();
		e.item = item;
		e.type = SWT.PaintItem;
		e.index = index;
		// gc.setClipping(x, y, width, height);
		e.gc = gc;
		e.x = x;
		e.y = y;
		e.width = width;
		e.height = height;
		this.notifyListeners(SWT.PaintItem, e);
	}

	/**
	 * Send a selection event for a gallery item
	 * 
	 * @param item
	 */
	protected void notifySelectionListeners(GalleryItem item, int index) {

		Event e = new Event();
		e.widget = this;
		e.item = item;
		e.data = item.getData();
		// TODO: enable e.index
		// e.index = index;
		try {
			notifyListeners(SWT.Selection, e);
		} catch (RuntimeException e1) {
		}
	}

	/**
	 * Create a Gallery
	 * 
	 * 
	 * @param parent
	 * @param style -
	 *            SWT.VIRTUAL switches in virtual mode. <br/>SWT.V_SCROLL add
	 *            vertical slider and switches to vertical mode.
	 *            <br/>SWT.H_SCROLL add horizontal slider and switches to
	 *            horizontal mode. <br/>if both V_SCROLL and H_SCROLL are
	 *            specified, the gallery is in vertical mode by default. Mode
	 *            can be changed afterward using setVertical<br/> SWT.MULTI
	 *            allows only several items to be selected at the same time.
	 */
	public Gallery(Composite parent, int style) {
		super(parent, style | SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
		virtual = (style & SWT.VIRTUAL) > 0;
		vertical = (style & SWT.V_SCROLL) > 0;
		multi = (style & SWT.MULTI) > 0;
		backgroundColor = getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);

		// Dispose renderers on dispose
		this.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if (itemRenderer != null)
					itemRenderer.dispose();

				if (groupRenderer != null)
					groupRenderer.dispose();
			}
		});

		// Add listeners : redraws, mouse and keyboard
		_addResizeListeners();
		_addPaintListeners();
		_addScrollBarsListeners();
		_addMouseListeners();

		// Layout
		updateStructuralValues(false);
		updateScrollBarsProperties();
		redraw();
	}

	/**
	 * Add internal paint listeners to this gallery.
	 */
	private void _addPaintListeners() {
		addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent event) {
				paint(event.gc);
			}
		});
	}

	/**
	 * Add internal resize listeners to this gallery.
	 */
	private void _addResizeListeners() {
		addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent event) {
				updateStructuralValues(true);
				updateScrollBarsProperties();
				redraw();
			}
		});
	}

	/**
	 * Add internal scrollbars listeners to this gallery.
	 */
	private void _addScrollBarsListeners() {
		// Vertical bar
		ScrollBar verticalBar = getVerticalBar();
		if (verticalBar != null) {
			verticalBar.addSelectionListener(new SelectionAdapter() {

				public void widgetSelected(SelectionEvent event) {
					if (vertical)
						scrollVertical();
				}
			});
		}

		// Horizontal bar

		ScrollBar horizontalBar = getHorizontalBar();
		if (horizontalBar != null) {
			horizontalBar.addSelectionListener(new SelectionAdapter() {

				public void widgetSelected(SelectionEvent event) {
					if (!vertical)
						scrollHorizontal();
				}
			});
		}

	}

	/**
	 * Add internal mouse listeners to this gallery.
	 */
	private void _addMouseListeners() {
		addMouseListener(new MouseListener() {

			public void mouseDoubleClick(MouseEvent e) {
				GalleryItem item = getItem(new Point(e.x, e.y));
				if (item != null) {
					// TODO: Handle double click.

				}

			}

			public void mouseDown(MouseEvent e) {
				if (DEBUG)
					System.out.println("Mouse down ");

				if (!_mouseDown(e)) {
					return;
				}

				if (e.button == 1) {
					GalleryItem item = getItem(new Point(e.x, e.y));

					if (item == null) {
						_deselectAll();
						redraw();
					} else {
						if ((e.stateMask & SWT.MOD1) == 0 && (e.stateMask & SWT.SHIFT) == 0) {

							if (!isSelected(item)) {
								_deselectAll();

								if (DEBUG)
									System.out.println("setSelected");
								setSelected(item, true, true);

								lastSingleClick = item;
								redraw();
							}
						}
					}
				} else if (e.button == 3) {
					if (DEBUG)
						System.out.println("right clic");
					GalleryItem item = getItem(new Point(e.x, e.y));
					if (!isSelected(item)) {
						_deselectAll();
						setSelected(item, true, true);
						redraw();
					}

				}

			}

			public void mouseUp(MouseEvent e) {
				if (DEBUG)
					System.out.println("Mouse Up ");
				if (e.button == 1) {
					GalleryItem item = getItem(new Point(e.x, e.y));
					if (item == null)
						return;

					if ((e.stateMask & SWT.MOD1) > 0) {
						if (item != null) {
							if (DEBUG)
								System.out.println("setSelected : inverse");
							setSelected(item, !isSelected(item), true);
							lastSingleClick = item;
							redraw();
						}
					} else if ((e.stateMask & SWT.SHIFT) > 0) {
						_deselectAll();

						if (getOrder(item, lastSingleClick))
							select(item, lastSingleClick);
						else
							select(lastSingleClick, item);

					} else {
						if (item == null) {
							_deselectAll();
						} else {
							if (DEBUG)
								System.out.println("setSelected");

							_deselectAll();
							setSelected(item, true, lastSingleClick != item);
							lastSingleClick = item;

						}
						redraw();
					}
				}

			}

		});
	}

	private void select(int from, int to) {
		for (int i = from; i <= to; i++) {
			GalleryItem item = getItem(i);
			this._addSelection(item);
			item._selectAll();

		}
	}

	private void select(GalleryItem from, GalleryItem to) {
		GalleryItem fromParent = from.getParentItem();
		GalleryItem toParent = to.getParentItem();

		if (fromParent == toParent) {

			if (fromParent == null) {
				int fromIndex = indexOf(from);
				int toIndex = indexOf(to);
				select(fromIndex, toIndex);
			} else {
				int fromIndex = fromParent.indexOf(from);
				int toIndex = toParent.indexOf(to);
				fromParent.select(fromIndex, toIndex);
			}
		}
		this.notifySelectionListeners(to, indexOf(to));
		redraw();
	}

	private boolean getOrder(GalleryItem before, GalleryItem after) {

		GalleryItem newParent = before.getParentItem();
		GalleryItem oldParent = after.getParentItem();

		if (newParent == oldParent) {
			int newParentIndex;
			int oldParentIndex;
			if (newParent == null) {
				newParentIndex = indexOf(before);
				oldParentIndex = indexOf(after);

			} else {
				newParentIndex = newParent.indexOf(before);
				oldParentIndex = newParent.indexOf(after);
			}
			return (newParentIndex < oldParentIndex);
		}

		// TODO : handle case when item don't have the same parent
		return true;
	}

	/**
	 * Toggle item selection status
	 * 
	 * @param i
	 * @param selected
	 * @param notifyListeners
	 *            TODO
	 */
	private void setSelected(GalleryItem item, boolean selected, boolean notifyListeners) {
		if (selected) {
			if (!isSelected(item)) {
				_addSelection(item);

				// Notify listeners if necessary.
				if (notifyListeners)
					notifySelectionListeners(item, indexOf(item));
			}

		} else {
			if (isSelected(item)) {
				_removeSelection(item);
			}
		}
	}

	protected void _addSelection(GalleryItem item) {
		// Deselect all items is multi selection is disabled
		if (!multi) {
			_deselectAll();
		}

		if (item.getParentItem() != null) {
			item.getParentItem()._addSelection(item);
		} else {
			if (selectionIndices == null) {
				selectionIndices = new int[1];
			} else {
				int[] oldSelection = selectionIndices;
				selectionIndices = new int[oldSelection.length + 1];
				System.arraycopy(oldSelection, 0, selectionIndices, 0, oldSelection.length);
			}
			selectionIndices[selectionIndices.length - 1] = indexOf(item);

		}

		if (selection == null) {
			selection = new GalleryItem[1];
		} else {
			GalleryItem[] oldSelection = selection;
			selection = new GalleryItem[oldSelection.length + 1];
			System.arraycopy(oldSelection, 0, selection, 0, oldSelection.length);
		}
		selection[selection.length - 1] = item;

	}

	private void _removeSelection(GalleryItem item) {
		if (selection == null)
			return;
		if (selection.length == 1) {
			selection = null;
		} else {
			int index = indexOf(item);

			GalleryItem[] oldSelection = selection;
			selection = new GalleryItem[oldSelection.length - 1];

			// TODO: check this part
			if (index > 0)
				System.arraycopy(oldSelection, 0, selection, 0, index - 1);

			if (index + 1 < oldSelection.length)
				System.arraycopy(oldSelection, index + 1, selection, index, selection.length - index - 1);

		}

	}

	protected boolean isSelected(GalleryItem item) {

		if (item.getParentItem() != null) {
			return item.getParentItem().isSelected(item);
		} else {
			int index = indexOf(item);
			for (int i = 0; i < selectionIndices.length; i++) {
				if (selectionIndices[i] == index)
					return true;
			}
		}
		return false;
	}

	public void deselectAll() {
		checkWidget();
		_deselectAll();

		redraw();
	}

	protected void _deselectAll() {

		if (DEBUG)
			System.out.println("clear");

		this.selection = null;
		this.selectionIndices = null;

		if (items == null)
			return;
		for (int i = 0; i < items.length; i++) {
			if (items[i] != null)
				items[i]._deselectAll();
		}

	}

	private void paint(GC gc) {
		if (DEBUG)
			System.out.println("paint");

		GC newGC = gc;

		// Linux-GTK Bug 174932
		if (!SWT.getPlatform().equals("gtk")) {
			newGC.setAdvanced(true);
		}

		if (gc.getAdvanced()) {
			newGC.setAntialias(antialias);
			newGC.setInterpolation(interpolation);
		}

		Rectangle clipping = newGC.getClipping();
		gc.setBackground(backgroundColor);
		drawBackground(newGC, clipping.x, clipping.y, clipping.width, clipping.height);

		int[] indexes = getVisibleItems(clipping);

		if (indexes != null && indexes.length > 0) {

			// Call preDraw for optimization
			if (groupRenderer != null)
				groupRenderer.preDraw(newGC);
			if (itemRenderer != null)
				itemRenderer.preDraw(newGC);

			for (int i = indexes.length - 1; i >= 0; i--) {
				if (DEBUG)
					System.out.println("Drawing group " + indexes[i]);

				_drawGroup(newGC, indexes[i]);
			}
		}

	}

	private int[] getVisibleItems(Rectangle clipping) {

		if (items == null)
			return null;

		int start = vertical ? (clipping.y + translate) : (clipping.x + translate);

		int end = vertical ? (clipping.y + clipping.height + translate) : (clipping.x + clipping.width + translate);

		ArrayList al = new ArrayList();
		int index = 0;
		GalleryItem item = null;
		while (index < items.length) {
			item = getItem(index);
			if ((vertical ? item.y : item.x) > end)
				break;

			if ((vertical ? (item.y + item.height) : (item.x + item.width)) >= start)
				al.add(new Integer(index));

			index++;
		}

		int[] result = new int[al.size()];

		for (int i = 0; i < al.size(); i++)
			result[i] = ((Integer) al.get(i)).intValue();

		return result;
	}

	public void refresh(int nb) {
		checkWidget();
		if (nb < getItemCount()) {
			// TODO: refresh
		}
	}

	public void redraw(GalleryItem item) {
		checkWidget();
	}

	/**
	 * Draw a group. Used when useGroup is true and for root items.
	 * 
	 * @param gc
	 * @param index
	 */
	private void _drawGroup(GC gc, int index) {
		// Draw group
		GalleryItem item = getItem(index);
		if (item == null)
			return;
		this.groupRenderer.setExpanded(item.isExpanded());

		// Drawing area
		int x = this.vertical ? item.x : item.x - this.translate;
		int y = this.vertical ? item.y - translate : item.y;

		Rectangle clipping = gc.getClipping();
		this.groupRenderer.draw(gc, item, x, y, clipping.x, clipping.y, clipping.width, clipping.height);
	}

	/**
	 * If table is virtual and item at pos i has not been set, call the callback
	 * listener to set its value.
	 * 
	 * @return
	 */
	private void updateItem(GalleryItem parentItem, int i) {

		GalleryItem galleryItem;
		if (parentItem == null) {
			// Parent is the Gallery widget
			galleryItem = items[i];
			if (galleryItem == null && this.virtual) {
				if (DEBUG) {
					System.out.println("Virtual/creating item ");
				}

				galleryItem = new GalleryItem(this, SWT.NONE);
				items[i] = galleryItem;
				setData(galleryItem, i);
			}
		} else {
			// Parent is another GalleryItem
			galleryItem = parentItem.items[i];
			if (galleryItem == null && this.virtual) {
				if (DEBUG) {
					System.out.println("Virtual/creating item ");
				}

				galleryItem = new GalleryItem(parentItem, SWT.NONE);
				parentItem.items[i] = galleryItem;
				setData(galleryItem, i);
			}
		}

	}

	private void setData(GalleryItem galleryItem, int index) {
		Item item = galleryItem;
		Event e = new Event();
		e.item = item;
		e.type = SWT.SetData;
		e.index = index;
		this.notifyListeners(SWT.SetData, e);
	}

	/**
	 * Recalculate structural values using the group renderer<br>
	 * Gallery and item size will be updated.
	 * 
	 * @param keepLocation
	 *            if true, the current scrollbars position ratio is saved and
	 *            restored even if the gallery size has changed. (Visible items
	 *            stay visible)
	 */
	protected void updateStructuralValues(boolean keepLocation) {

		if (DEBUG)
			System.out.println("Client Area : " + this.getClientArea().x + " " + this.getClientArea().y + " " + this.getClientArea().width + " "
					+ this.getClientArea().height);

		Rectangle area = this.getClientArea();
		float pos = 0;

		if (vertical) {
			if (gHeight > 0 && keepLocation)
				pos = (float) (translate + 0.5 * area.height) / (float) gHeight;

			gWidth = area.width;
			gHeight = calculateSize();

			if (keepLocation)
				translate = (int) (gHeight * pos - 0.5 * area.height);

		} else {
			if (gWidth > 0 && keepLocation)
				pos = (float) (translate + 0.5 * area.width) / (float) gWidth;

			gWidth = calculateSize();
			gHeight = area.height;

			if (keepLocation)
				translate = (int) (gWidth * pos - 0.5 * area.width);
		}

		validateTranslation();
		if (DEBUG)
			System.out.println("Content Size : " + gWidth + " " + gHeight);

	}

	private int calculateSize() {

		if (groupRenderer != null)
			groupRenderer.preLayout(null);

		int currentHeight = 0;

		int mainItemCount = getItemCount();

		for (int i = 0; i < mainItemCount; i++) {
			GalleryItem item = this.getItem(i);
			this.groupRenderer.setExpanded(item.isExpanded());
			int groupItemCount = item.getItemCount();
			if (vertical) {
				item.y = currentHeight;
				item.x = getClientArea().x;
				item.width = getClientArea().width;
				item.height = -1;
				this.groupRenderer.layout(null, item);
				currentHeight += item.height;
			} else {
				item.y = getClientArea().y;
				item.x = currentHeight;
				item.width = -1;
				item.height = getClientArea().height;
				this.groupRenderer.layout(null, item);
				currentHeight += item.width;
			}
			// Point s = this.getSize(item.hCount, item.vCount, itemSizeX,
			// itemSizeY, userMargin, realMargin);

			// item.height = s.y;

		}

		return currentHeight;
	}

	/**
	 * Move the scrollbar to reflect the current visible items position.
	 * <br/>The bar which is moved depends of the current gallery scrolling :
	 * vertical or horizontal.
	 * 
	 */
	protected void updateScrollBarsProperties() {

		if (vertical) {
			updateScrollBarProperties(getVerticalBar(), getClientArea().height, gHeight);
		} else {
			updateScrollBarProperties(getHorizontalBar(), getClientArea().width, gWidth);
		}

	}

	/**
	 * Move the scrollbar to reflect the current visible items position.
	 * 
	 * @param bar -
	 *            the scroll bar to move
	 * @param clientSize -
	 *            Client (visible) area size
	 * @param totalSize -
	 *            Total Size
	 */
	private void updateScrollBarProperties(ScrollBar bar, int clientSize, int totalSize) {
		if (bar == null)
			return;

		bar.setMinimum(0);

		bar.setIncrement(16);
		bar.setPageIncrement(clientSize);
		bar.setMaximum(totalSize);
		bar.setThumb(clientSize);

		if (totalSize > clientSize) {
			if (DEBUG)
				System.out.println("Enabling scrollbar");

			bar.setEnabled(true);
			bar.setSelection(translate);

			// Ensure that translate has a valid value.
			validateTranslation();
		} else {
			if (DEBUG)
				System.out.println("Disabling scrollbar");

			bar.setEnabled(false);
			bar.setSelection(0);
			translate = 0;
		}

	}

	/**
	 * Check the current translation value. Must be &gt; 0 and &lt; gallery
	 * size.<br/> Invalid values are fixed.
	 */
	private void validateTranslation() {
		Rectangle area = this.getClientArea();
		// Ensure that translate has a valid value.
		int totalSize = 0;
		int clientSize = 0;

		// Fix negative values
		if (translate < 0)
			translate = 0;

		// Get size depending on vertical setting.
		if (vertical) {
			totalSize = gHeight;
			clientSize = area.height;
		} else {
			totalSize = gWidth;
			clientSize = area.width;
		}

		if (totalSize > clientSize) {
			// Fix translate too big.
			if (translate + clientSize > totalSize)
				translate = totalSize - clientSize;
		} else
			translate = 0;

	}

	private void scroll() {
		if (vertical)
			scrollVertical();
		else
			scrollHorizontal();
	}

	private void scrollVertical() {
		int areaHeight = getClientArea().height;

		if (gHeight > areaHeight) {
			// image is higher than client area
			ScrollBar bar = getVerticalBar();
			scroll(0, translate - bar.getSelection(), 0, 0, getClientArea().width, areaHeight, false);
			translate = bar.getSelection();
		} else {
			translate = 0;
		}
	}

	private void scrollHorizontal() {

		int areaWidth = getClientArea().width;
		if (gWidth > areaWidth) {
			// image is higher than client area
			ScrollBar bar = getHorizontalBar();
			scroll(translate - bar.getSelection(), 0, 0, 0, areaWidth, getClientArea().height, false);
			translate = bar.getSelection();
		} else {
			translate = 0;
		}

	}

	protected void addItem(GalleryItem i) {
		checkWidget();
		if (!virtual) {

			if (items == null) {
				items = new GalleryItem[1];
			} else {
				Item[] oldItems = items;
				items = new GalleryItem[oldItems.length + 1];
				System.arraycopy(oldItems, 0, items, 0, oldItems.length);
			}
			items[items.length - 1] = i;
			updateStructuralValues(false);
			updateScrollBarsProperties();
		}
	}

	/**
	 * Get the item at index.<br/> If SWT.VIRTUAL is used and the item has not
	 * been used yet, the item is created and a SWT.SetData event is fired.
	 * 
	 * @param index :
	 *            index of the item.
	 * @return : the GalleryItem or null if index is out of bounds
	 */
	public GalleryItem getItem(int index) {
		checkWidget();
		return _getItem(index);
	}

	/**
	 * This method is used by items to implement getItem( index)
	 * 
	 * @param parent
	 * @param index
	 * @return
	 */
	protected GalleryItem _getItem(GalleryItem parent, int index) {

		if (index < parent.getItemCount()) {
			// System.out.println( "getItem " + index);

			// Refresh item if it is not set yet
			updateItem(parent, index);
			return parent.items[index];
		}

		return null;
	}

	/**
	 * Get the item at index.<br/> If SWT.VIRTUAL is used and the item has not
	 * been used, the item is created and a SWT.SetData is fired.<br/>
	 * 
	 * This is the internat implementation of this method : checkWidget() is not
	 * used.
	 * 
	 * @param index
	 * @return
	 */
	protected GalleryItem _getItem(int index) {

		if (index < getItemCount()) {
			updateItem(null, index);
			return items[index];
		}

		return null;
	}

	/**
	 * Forward the mouseDown event to the corresponding group accorrding to the
	 * mouse position.
	 * 
	 * @param e
	 * @return
	 */
	protected boolean _mouseDown(MouseEvent e) {
		if (DEBUG)
			System.out.println("getitem " + e.x + " " + e.y);

		GalleryItem group = this.getGroup(new Point(e.x, e.y));
		if (group != null) {
			int pos = vertical ? (e.y + translate) : (e.x + translate);
			return groupRenderer.mouseDown(group, e, new Point(vertical ? e.x : pos, vertical ? pos : e.y));
		}

		return true;
	}

	/**
	 * Get item at pixel position
	 * 
	 * @param coords
	 * @return
	 */
	public GalleryItem getItem(Point coords) {
		checkWidget();

		if (DEBUG)
			System.out.println("getitem " + coords.x + " " + coords.y);
		int pos = vertical ? (coords.y + translate) : (coords.x + translate);

		GalleryItem group = this.getGroup(coords);
		if (group != null)
			return groupRenderer.getItem(group, new Point(vertical ? coords.x : pos, vertical ? pos : coords.y));

		return null;
	}

	/**
	 * Get group at pixel position
	 * 
	 * @param coords
	 * @return
	 */
	private GalleryItem getGroup(Point coords) {
		int pos = vertical ? (coords.y + translate) : (coords.x + translate);

		int index = 0;
		GalleryItem item = null;
		while (index < items.length) {
			item = getItem(index);

			if ((vertical ? item.y : item.x) > pos)
				break;

			if ((vertical ? (item.y + item.height) : (item.x + item.width)) >= pos)
				return item;

			index++;
		}

		return null;
	}

	private void clear() {
		checkWidget();
		if (virtual) {
			setItemCount(0);
		} else {
			items = null;
		}

		updateStructuralValues(true);
		updateScrollBarsProperties();
	}

	/**
	 * Clear all items.<br/>
	 * 
	 * All items are removed and dispose events are fired if the gallery is not
	 * virtual.<br/>
	 * 
	 * If the Gallery is virtual, the item count is not reseted and all items
	 * will be created again at their first use.<br/>
	 * 
	 */
	public void clearAll() {
		checkWidget();
		if (items != null) {
			// Clear items
			for (int i = 0; i < items.length; i++) {

				// Dispose items if not virtual
				if (!virtual) {
					if (items[i] != null) {
						// TODO: send a dispose event
						items[i].dispose();
					}
				}

				// Empty item
				items[i] = null;
			}
		}

		// Free array if not virtual
		if (!virtual) {
			items = null;
		}

		// TODO: I'm clearing selection here
		// but we have to check that Table has the same behavior
		this._deselectAll();

		updateStructuralValues(false);
		updateScrollBarsProperties();
		redraw();

	}

	/**
	 * Clear one item.<br/>
	 * 
	 * @param i
	 */
	public void clear(int i) {
		checkWidget();

		// TODO: When a Gallery is not virtual
		// Item must be removed and a dispose event must be fired.

		if (virtual) {
			items[i] = null;

			updateStructuralValues(false);
			updateScrollBarsProperties();
		}
	}

	/**
	 * Returns the index of a GalleryItem.
	 * 
	 * @param parentItem
	 * @param item
	 * @return
	 */
	public int indexOf(GalleryItem item) {
		checkWidget();
		if (item.getParentItem() == null)
			return _indexOf(item);
		else
			return _indexOf(item.getParentItem(), item);
	}

	/**
	 * Returns the index of a GalleryItem when it is a root Item
	 * 
	 * @param parentItem
	 * @param item
	 * @return
	 */
	protected int _indexOf(GalleryItem item) {
		int itemCount = getItemCount();
		if (item == null)
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (1 <= lastIndexOf && lastIndexOf < itemCount - 1) {
			if (items[lastIndexOf] == item)
				return lastIndexOf;
			if (items[lastIndexOf + 1] == item)
				return ++lastIndexOf;
			if (items[lastIndexOf - 1] == item)
				return --lastIndexOf;
		}
		if (lastIndexOf < itemCount / 2) {
			for (int i = 0; i < itemCount; i++) {
				if (items[i] == item)
					return lastIndexOf = i;
			}
		} else {
			for (int i = itemCount - 1; i >= 0; --i) {
				if (items[i] == item)
					return lastIndexOf = i;
			}
		}
		return -1;
	}

	/**
	 * Returns the index of a GalleryItem when it is not a root Item
	 * 
	 * @param parentItem
	 * @param item
	 * @return
	 */
	protected int _indexOf(GalleryItem parentItem, GalleryItem item) {
		int itemCount = parentItem.getItemCount();
		if (item == null)
			SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (1 <= parentItem.lastIndexOf && parentItem.lastIndexOf < itemCount - 1) {
			if (parentItem.items[parentItem.lastIndexOf] == item)
				return parentItem.lastIndexOf;
			if (parentItem.items[parentItem.lastIndexOf + 1] == item)
				return ++parentItem.lastIndexOf;
			if (parentItem.items[parentItem.lastIndexOf - 1] == item)
				return --parentItem.lastIndexOf;
		}
		if (parentItem.lastIndexOf < itemCount / 2) {
			for (int i = 0; i < itemCount; i++) {
				if (parentItem.items[i] == item)
					return parentItem.lastIndexOf = i;
			}
		} else {
			for (int i = itemCount - 1; i >= 0; --i) {
				if (parentItem.items[i] == item)
					return parentItem.lastIndexOf = i;
			}
		}
		return -1;
	}

	public GalleryItem[] getItems() {
		checkWidget();
		GalleryItem[] itemsLocal = new GalleryItem[this.items.length];
		System.arraycopy(items, 0, itemsLocal, 0, this.items.length);

		return itemsLocal;
	}

	public boolean isVertical() {
		checkWidget();
		return vertical;
	}

	public void setVertical(boolean vertical) {
		checkWidget();
		this.vertical = vertical;
		this.updateStructuralValues(true);
		redraw();
	}

	public AbstractGalleryGroupRenderer getGroupRenderer() {
		return groupRenderer;
	}

	public void setGroupRenderer(AbstractGalleryGroupRenderer groupRenderer) {
		this.groupRenderer = groupRenderer;
		groupRenderer.setGallery(this);
		this.updateStructuralValues(true);
		this.updateScrollBarsProperties();
		this.redraw();
	}

	public GalleryItem[] getSelection() {
		return selection;
	}

	public int getSelectionCount() {
		if (selection == null)
			return 0;

		return selection.length;
	}

	public void selectAll() {
		checkWidget();
		_selectAll();
		redraw();
	}

	public void _selectAll() {
		select(0, this.getItemCount() - 1);
	}
}
