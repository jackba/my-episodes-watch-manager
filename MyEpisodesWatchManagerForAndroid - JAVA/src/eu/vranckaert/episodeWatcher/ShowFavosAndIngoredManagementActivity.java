package eu.vranckaert.episodeWatcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import eu.vranckaert.episodeWatcher.domain.Show;
import eu.vranckaert.episodeWatcher.domain.User;
import eu.vranckaert.episodeWatcher.enums.ShowAction;
import eu.vranckaert.episodeWatcher.enums.ShowType;
import eu.vranckaert.episodeWatcher.exception.InternetConnectivityException;
import eu.vranckaert.episodeWatcher.exception.LoginFailedException;
import eu.vranckaert.episodeWatcher.exception.UnsupportedHttpPostEncodingException;
import eu.vranckaert.episodeWatcher.preferences.Preferences;
import eu.vranckaert.episodeWatcher.preferences.PreferencesKeys;
import eu.vranckaert.episodeWatcher.service.ShowService;

import java.util.ArrayList;
import java.util.List;

public class ShowFavosAndIngoredManagementActivity extends ListActivity {
    private static final String LOG_TAG = ShowFavosAndIngoredManagementActivity.class.getSimpleName();
    private ShowType showType;
    private User user;
    private ShowService service;
    private ShowAdapter showAdapter;
    private List<Show> shows = new ArrayList<Show>(0);

    private int showListPosition = -1;
    private Integer exceptionMessageResId = null;

    private static final int DIALOG_LOADING = 0;
    private static final int DIALOG_EXCEPTION = 1;

    private static final int CONTEXT_MENU_DELETE = 0;
    private static final int CONTEXT_MENU_UNIGNORE = 1;
    private static final int CONTEXT_MENU_IGNORE = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	init(savedInstanceState);

        reloadShows();
    }

    private void init(Bundle savedInstanceState) {
        setTheme(Preferences.getPreferenceInt(this, PreferencesKeys.THEME_KEY) == 0 ? android.R.style.Theme_Light : android.R.style.Theme);
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.showfavosandignoredmanagement);

        Bundle data = this.getIntent().getExtras();
        showType = (ShowType) data.get(ShowType.class.getSimpleName());

        if(showType.equals(ShowType.FAVOURITE_SHOWS)) {
            Log.d(LOG_TAG, "Opening the favourite shows");
        } else if(showType.equals(ShowType.IGNORED_SHOWS)) {
            Log.d(LOG_TAG, "Opening the ignored shows");
        }

        user = new User(
            Preferences.getPreference(this, User.USERNAME),
            Preferences.getPreference(this, User.PASSWORD)
        );

        initializeShowList();

        service = new ShowService();
    }

    @Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
			case DIALOG_LOADING: {
				ProgressDialog progressDialog = new ProgressDialog(this);
				progressDialog.setMessage(this.getString(R.string.progressLoadingTitle));
                progressDialog.setCancelable(false);
				dialog = progressDialog;
				break;
            }
            case DIALOG_EXCEPTION: {
				if (exceptionMessageResId == null) {
					exceptionMessageResId = R.string.defaultExceptionMessage;
				}
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.exceptionDialogTitle)
					   .setMessage(exceptionMessageResId)
					   .setCancelable(false)
					   .setPositiveButton(R.string.dialogOK, new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
                                exceptionMessageResId = null;
                                removeDialog(DIALOG_EXCEPTION);
				           }
				       });
				dialog = builder.create();
                break;
            }
        }
        return dialog;
    }

    private void reloadShows() {
        AsyncTask<Void, Void, Void> asyncTask = new AsyncTask() {
            @Override
            protected void onPreExecute() {
                showDialog(DIALOG_LOADING);
            }

            @Override
            protected Object doInBackground(Object... objects) {
                getShows(user, showType);
                return 100L;
            }

            @Override
            protected void onPostExecute(Object o) {
                if(exceptionMessageResId != null && !exceptionMessageResId.equals("")) {
                    removeDialog(DIALOG_LOADING);
                    showDialog(DIALOG_EXCEPTION);
                } else {
                    updateShowList();
                    removeDialog(DIALOG_LOADING);
                }
            }
        };
        asyncTask.execute();
    }

    private void getShows(User user, ShowType showType) {
        try {
            shows = service.getFavoriteOrIgnoredShows(user, showType);
        } catch (UnsupportedHttpPostEncodingException e) {
            String message = "Network issues";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        } catch (InternetConnectivityException e) {
            String message = "Could not connect to host";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.internetConnectionFailureReload;
        } catch (LoginFailedException e) {
            String message = "Login failure";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        }
    }

    private void updateShowList() {
        showAdapter.clear();
        for(Show show : shows) {
            showAdapter.add(show);
        }
        showAdapter.notifyDataSetChanged();
    }

    private void initializeShowList() {
        showAdapter = new ShowAdapter(this, R.layout.searchresultsshowsrow, shows);
        setListAdapter(showAdapter);
        registerForContextMenu(getListView());
    }

    private class ShowAdapter extends ArrayAdapter<Show> {
        private List<Show> shows;

        public ShowAdapter(Context context, int textViewResourceId, List<Show> el) {
            super(context, textViewResourceId, el);
            this.shows = el;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final int i = position;
            View row = convertView;
            if (row==null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.searchresultsshowsrow, parent, false);
            }

            TextView topText = (TextView) row.findViewById(R.id.showNameSearchResult);

            Show show = shows.get(position);
            topText.setText(show.getShowName());

            return row;
        }
    }

    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        switch(showType) {
            case FAVOURITE_SHOWS:
                menu.add(Menu.NONE, CONTEXT_MENU_IGNORE, Menu.NONE, R.string.favoIgnoredIgnoreShow);
                break;
            case IGNORED_SHOWS:
                menu.add(Menu.NONE, CONTEXT_MENU_UNIGNORE, Menu.NONE, R.string.favoIgnoredUnignoreShow);
                break;
        }

        menu.add(Menu.NONE, CONTEXT_MENU_DELETE, Menu.NONE, R.string.favoIgnoredDeleteShow);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Show show = shows.get(info.position);
        switch(item.getItemId()) {
            case CONTEXT_MENU_IGNORE:
                markShow(show, ShowAction.IGNORE);
                break;
            case CONTEXT_MENU_UNIGNORE:
                markShow(show, ShowAction.UNIGNORE);
                break;
            case CONTEXT_MENU_DELETE:
                markShow(show, ShowAction.DELETE);
                break;
            default:
                return false;
        }
        return true;
    }

    private void markShow(final Show show, final ShowAction action) {
        AsyncTask<Void, Void, Void> asyncTask = new AsyncTask() {
            @Override
            protected void onPreExecute() {
                showDialog(DIALOG_LOADING);
            }

            @Override
            protected Object doInBackground(Object... objects) {
                markShow(user, showType, action, show);
                return 100L;
            }

            @Override
            protected void onPostExecute(Object o) {
                if(exceptionMessageResId != null && !exceptionMessageResId.equals("")) {
                    removeDialog(DIALOG_LOADING);
                    showDialog(DIALOG_EXCEPTION);
                } else {
                    updateShowList();
                    removeDialog(DIALOG_LOADING);
                }
            }
        };
        asyncTask.execute();
    }

    private void markShow(User user, ShowType showType, ShowAction showAction, Show show) {
        try {
            shows = service.markShow(user, show, showAction, showType);
        } catch (UnsupportedHttpPostEncodingException e) {
            String message = "Network issues";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        } catch (InternetConnectivityException e) {
            String message = "Could not connect to host";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.internetConnectionFailureReload;
        } catch (LoginFailedException e) {
            String message = "Login failure";
			Log.e(LOG_TAG, message, e);
			exceptionMessageResId = R.string.networkIssues;
        }
    }
}
