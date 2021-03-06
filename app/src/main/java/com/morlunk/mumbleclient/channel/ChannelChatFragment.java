/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.mumbleclient.channel;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.morlunk.jumble.IJumbleObserver;
import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.util.JumbleServiceFragment;
import com.morlunk.mumbleclient.util.MumbleImageGetter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelChatFragment extends JumbleServiceFragment implements ChatTargetProvider.OnChatTargetSelectedListener {
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final String CHAT_DATE_FORMAT = "%I:%M %p";

	private IJumbleObserver mServiceObserver = new JumbleObserver() {

        @Override
        public void onMessageLogged(Message message) throws RemoteException {
            addChatMessage(message, true);
        }
    };

    private ListView mChatList;
    private ChannelChatAdapter mChatAdapter;
	private EditText mChatTextEdit;
	private ImageButton mSendButton;
    private ChatTargetProvider mTargetProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mTargetProvider = (ChatTargetProvider) getParentFragment();
        } catch (ClassCastException e) {
            throw new ClassCastException(getParentFragment().toString()+" must implement ChatTargetProvider");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mTargetProvider.registerChatTargetListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mTargetProvider.unregisterChatTargetListener(this);
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_chat, container, false);
        mChatList = (ListView) view.findViewById(R.id.chat_list);
		mChatTextEdit = (EditText) view.findViewById(R.id.chatTextEdit);
		
		mSendButton = (ImageButton) view.findViewById(R.id.chatTextSend);
		mSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    sendMessage(mChatTextEdit.getText().toString());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                mChatTextEdit.setText("");
            }
        });
		
		mChatTextEdit.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                try {
                    sendMessage(v.getText().toString());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                v.setText("");
                return true;
            }
        });
		
		mChatTextEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSendButton.setEnabled(mChatTextEdit.getText().length() > 0);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        try {
            updateChatTargetText(mTargetProvider.getChatTarget());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return view;
	}

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear_chat:
                clear();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Adds the passed text to the fragment chat body.
     * @param message The message to add.
     * @param scroll Whether to scroll to the bottom after adding the message.
     */
    public void addChatMessage(Message message, boolean scroll) {
		if(mChatAdapter == null) return;

        mChatAdapter.add(message);

        if(scroll) {
            mChatList.post(new Runnable() {

                @Override
                public void run() {
                    mChatList.smoothScrollToPosition(mChatAdapter.getCount() - 1);
                }
            });
        }
	}

	private void sendMessage(String message) throws RemoteException {
        String formattedMessage = linkifyOutgoingMessage(message);
        ChatTargetProvider.ChatTarget target = mTargetProvider.getChatTarget();
        Message responseMessage = null;
        if(target == null)
            responseMessage = getService().sendChannelTextMessage(getService().getSessionChannel().getId(), formattedMessage, false);
        else if(target.getUser() != null)
            responseMessage = getService().sendUserTextMessage(target.getUser().getSession(), formattedMessage);
        else if(target.getChannel() != null)
            responseMessage = getService().sendChannelTextMessage(target.getChannel().getId(), formattedMessage, false);
        addChatMessage(responseMessage, true);
	}

    private String linkifyOutgoingMessage(String message) {
        String formattedBody = message;
        Matcher matcher = LINK_PATTERN.matcher(formattedBody);
        formattedBody = matcher.replaceAll("<a href=\"$1\">$1</a>");
        return formattedBody;
    }
	
	public void clear() {
        mChatAdapter.clear();
        try {
            getService().clearMessageLog();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

	/**
	 * Updates hint displaying chat target.
	 */
	public void updateChatTargetText(ChatTargetProvider.ChatTarget target) throws RemoteException {
        if(getService() == null) return;

        if(target == null) mChatTextEdit.setHint(getString(R.string.messageToChannel, getService().getSessionChannel().getName()));
        else if(target.getUser() != null) mChatTextEdit.setHint(getString(R.string.messageToUser, target.getUser().getName()));
        else if(target.getChannel() != null) mChatTextEdit.setHint(getString(R.string.messageToChannel, target.getChannel().getName()));
	}


    @Override
    public void onServiceBound(IJumbleService service) {
        try {
            mChatAdapter = new ChannelChatAdapter(getActivity(), service, service.getMessageLog());
            mChatList.setAdapter(mChatAdapter);
            mChatList.post(new Runnable() {
                @Override
                public void run() {
                    mChatList.setSelection(mChatAdapter.getCount() - 1);
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IJumbleObserver getServiceObserver() {
        return mServiceObserver;
    }

    @Override
    public void onChatTargetSelected(ChatTargetProvider.ChatTarget target) {
        try {
            updateChatTargetText(target);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private static class ChannelChatAdapter extends ArrayAdapter<Message> {

        private MumbleImageGetter mImageGetter;
        private IJumbleService mService;

        public ChannelChatAdapter(Context context, IJumbleService service, List<Message> messages) {
            super(context, 0, new ArrayList<Message>(messages));
            mService = service;
            mImageGetter = new MumbleImageGetter(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if(v == null) {
                v = LayoutInflater.from(getContext()).inflate(R.layout.list_chat_item, parent, false);
            }

            LinearLayout chatBox = (LinearLayout) v.findViewById(R.id.list_chat_item_box);
            TextView targetText = (TextView) v.findViewById(R.id.list_chat_item_target);
            TextView messageText = (TextView) v.findViewById(R.id.list_chat_item_text);
            TextView timeText = (TextView) v.findViewById(R.id.list_chat_item_time);

            Message message = getItem(position);
            String targetMessage = null;
            boolean selfAuthored = false;
            try {
                selfAuthored = message.getActor() == mService.getSession();

                if((message.getChannels() != null && !message.getChannels().isEmpty()) || (message.getTrees() != null && !message.getTrees().isEmpty())) {
                    Channel currentChannel = message.getChannels().get(0);
                    targetMessage = getContext().getString(R.string.chat_message_to, message.getActorName(), currentChannel.getName());
                } else if(message.getUsers() != null && !message.getUsers().isEmpty()) {
                    User user = message.getUsers().get(0);
                    targetMessage = getContext().getString(R.string.chat_message_to, message.getActorName(), user.getName());
                } else {
                    targetMessage = message.getActorName();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            int gravity = selfAuthored ? Gravity.RIGHT : Gravity.LEFT;

            chatBox.setGravity(gravity);
            targetText.setVisibility(message.getType() == Message.Type.TEXT_MESSAGE ? View.VISIBLE : View.GONE);
            targetText.setText(targetMessage);
            messageText.setText(Html.fromHtml(message.getMessage(), mImageGetter, null));
            messageText.setMovementMethod(LinkMovementMethod.getInstance());
            messageText.setGravity(gravity);
            timeText.setText(message.getReceivedTime().format(CHAT_DATE_FORMAT));

            return v;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false; // Makes links clickable.
        }
    }
}
