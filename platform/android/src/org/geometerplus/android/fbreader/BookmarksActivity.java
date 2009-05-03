/*
 * Copyright (C) 2009 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader;

import java.util.*;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.Context;
import android.app.TabActivity;
import android.graphics.drawable.Drawable;

import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.impl.*;

import org.geometerplus.zlibrary.ui.android.R;

import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.fbreader.fbreader.FBReader;
import org.geometerplus.fbreader.library.*;

public class BookmarksActivity extends TabActivity {
	private static final int OPEN_ITEM_ID = 0;
	private static final int EDIT_ITEM_ID = 1;
	private static final int DELETE_ITEM_ID = 2;

	private List<Bookmark> myAllBooksBookmarks;
	private final List<Bookmark> myThisBookBookmarks = new LinkedList();
	private final List<Bookmark> mySearchResults = new LinkedList();

	private ListView myThisBookView;
	private ListView myAllBooksView;
	private ListView mySearchResultsView;

	private final ZLResource myResource = ZLResource.resource("bookmarksView");

	private ListView createTab(String tag, int id) {
		final TabHost host = getTabHost();
		final String label = myResource.getResource(tag).getValue();
		host.addTab(host.newTabSpec(tag).setIndicator(label).setContent(id));
		return (ListView)findViewById(id);
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

		final TabHost host = getTabHost();
		LayoutInflater.from(this).inflate(R.layout.bookmarks, host.getTabContentView(), true);

		myAllBooksBookmarks = Bookmark.bookmarks();
		Collections.sort(myAllBooksBookmarks, new Bookmark.ByTimeComparator());
		final long bookId = ((FBReader)FBReader.Instance()).Model.Book.getId();
		for (Bookmark bookmark : myAllBooksBookmarks) {
			if (bookmark.getBookId() == bookId) {
				myThisBookBookmarks.add(bookmark);
			}
		}

		myThisBookView = createTab("thisBook", R.id.this_book);
		new BookmarksAdapter(myThisBookView, myThisBookBookmarks, true);

		myAllBooksView = createTab("allBooks", R.id.all_books);
		new BookmarksAdapter(myAllBooksView, myAllBooksBookmarks, false);

		findViewById(R.id.search_results).setVisibility(View.GONE);
	}

	@Override
	public void onPause() {
		for (Bookmark bookmark : myAllBooksBookmarks) {
			bookmark.save();
		}
		super.onPause();
	}

	private void invalidateAllViews() {
		myThisBookView.invalidateViews();
		myThisBookView.requestLayout();
		myAllBooksView.invalidateViews();
		myAllBooksView.requestLayout();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final int position = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;
		final ListView view = (ListView)getTabHost().getCurrentView();
		final Bookmark bookmark = ((BookmarksAdapter)view.getAdapter()).getItem(position);
		switch (item.getItemId()) {
			case OPEN_ITEM_ID:
				gotoBookmark(bookmark);
				return true;
			case EDIT_ITEM_ID:
				// TODO: implement
				return true;
			case DELETE_ITEM_ID:
				bookmark.delete();
				myThisBookBookmarks.remove(bookmark);
				myAllBooksBookmarks.remove(bookmark);
				invalidateAllViews();
				return true;
		}
		return super.onContextItemSelected(item);
	}

	private void addBookmark() {
		final FBReader fbreader = (FBReader)FBReader.Instance();
		ZLTextWordCursor cursor = fbreader.BookTextView.getStartCursor();

		if (cursor.isNull()) {
			// TODO: implement
			return;
		}

		final ZLTextPosition position = new ZLTextPosition(cursor);
		final StringBuilder builder = new StringBuilder();
		int wordCounter = 0;
		cursor = new ZLTextWordCursor(cursor);

mainLoop:
		do {
			for (; !cursor.isEndOfParagraph(); cursor.nextWord()) {
				final ZLTextElement element = cursor.getElement();
				if (element instanceof ZLTextWord) {
					final ZLTextWord word = (ZLTextWord)element;
					if (builder.length() > 0) {
						builder.append(" ");
					}
					builder.append(word.Data, word.Offset, word.Length);
					if (++wordCounter >= 10) {
						break mainLoop;
					}
				}
			}
		} while ((builder.length() == 0) && cursor.nextParagraph());

		// TODO: text edit dialog
		final Bookmark bookmark = new Bookmark(fbreader.Model.Book, builder.toString(), position);
		myThisBookBookmarks.add(0, bookmark);
		myAllBooksBookmarks.add(0, bookmark);
		invalidateAllViews();
	}

	private void gotoBookmark(Bookmark bookmark) {
		bookmark.onOpen();
		final FBReader fbreader = (FBReader)FBReader.Instance();
		final long bookId = bookmark.getBookId();
		if (fbreader.Model.Book.getId() != bookId) {
			final Book book = Book.getById(bookId);
			if (book != null) {
				finish();
				fbreader.openBook(book, bookmark.getPosition());
			} else {
				Toast.makeText(
					this,
					ZLResource.resource("errorMessage").getResource("cannotOpenBook").getValue(),
					Toast.LENGTH_SHORT
				).show();
			}
		} else {
			finish();
			fbreader.BookTextView.gotoPosition(bookmark.getPosition());
		}
	}

	private final class BookmarksAdapter extends BaseAdapter implements AdapterView.OnItemClickListener, View.OnCreateContextMenuListener {
		private final List<Bookmark> myBookmarks;
		private final boolean myShowAddBookmarkButton;

		BookmarksAdapter(ListView listView, List<Bookmark> bookmarks, boolean showAddBookmarkButton) {
			myBookmarks = bookmarks;
			myShowAddBookmarkButton = showAddBookmarkButton;
			listView.setAdapter(this);
			listView.setOnItemClickListener(this);
			listView.setOnCreateContextMenuListener(this);
		}

		public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
			final int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
			if (position > 0) {
				menu.setHeaderTitle(getItem(position).getText());
				final ZLResource resource = ZLResource.resource("bookmarksView");
				menu.add(0, OPEN_ITEM_ID, 0, resource.getResource("open").getValue());
				menu.add(0, EDIT_ITEM_ID, 0, resource.getResource("edit").getValue());
				menu.add(0, DELETE_ITEM_ID, 0, resource.getResource("delete").getValue());
			}
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			final View view = (convertView != null) ? convertView :
				LayoutInflater.from(parent.getContext()).inflate(R.layout.bookmark_item, parent, false);
			final Bookmark bookmark = getItem(position);
			((ImageView)view.findViewById(R.id.bookmark_item_icon)).setImageResource(
				(bookmark != null) ? R.drawable.tree_icon_strut : R.drawable.tree_icon_plus
			);
			((TextView)view.findViewById(R.id.bookmark_item_text)).setText(
				(bookmark != null) ?
					bookmark.getText() :
					ZLResource.resource("bookmarksView").getResource("new").getValue()
			);
			return view;
		}

		public final boolean areAllItemsEnabled() {
			return true;
		}

		public final boolean isEnabled(int position) {
			return true;
		}

		public final long getItemId(int position) {
			return position;
		}
	
		public final Bookmark getItem(int position) {
			if (myShowAddBookmarkButton) {
				--position;
			}
			return (position >= 0) ? myBookmarks.get(position) : null;
		}

		public final int getCount() {
			return myShowAddBookmarkButton ? myBookmarks.size() + 1 : myBookmarks.size();
		}

		public final void onItemClick(AdapterView parent, View view, int position, long id) {
			final Bookmark bookmark = getItem(position);
			if (bookmark != null) {
				gotoBookmark(bookmark);
			} else {
				addBookmark();
			}
		}
	}
}
