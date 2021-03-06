/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package ir.besteveryeverapp.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.Semaphore;

import chitchat.FontManager;
import ir.besteveryeverapp.telegram.AndroidUtilities;
import ir.besteveryeverapp.telegram.FileLog;
import ir.besteveryeverapp.telegram.LocaleController;
import ir.besteveryeverapp.telegram.MessagesController;
import ir.besteveryeverapp.telegram.MessagesStorage;
import ir.besteveryeverapp.telegram.NotificationCenter;
import ir.besteveryeverapp.telegram.UserConfig;
import ir.besteveryeverapp.tgnet.TLRPC;
import ir.besteveryeverapp.ui.ActionBar.ActionBar;
import ir.besteveryeverapp.ui.ActionBar.ActionBarMenu;
import ir.besteveryeverapp.ui.ActionBar.BaseFragment;
import ir.besteveryeverapp.ui.Cells.ShadowSectionCell;
import ir.besteveryeverapp.ui.Cells.TextCheckCell;
import ir.besteveryeverapp.ui.Cells.TextInfoPrivacyCell;
import ir.besteveryeverapp.ui.Cells.TextSettingsCell;
import ir.besteveryeverapp.ui.Components.AvatarDrawable;
import ir.besteveryeverapp.ui.Components.AvatarUpdater;
import ir.besteveryeverapp.ui.Components.BackupImageView;
import ir.besteveryeverapp.ui.Components.LayoutHelper;

public class ChannelEditActivity extends BaseFragment implements AvatarUpdater.AvatarUpdaterDelegate, NotificationCenter.NotificationCenterDelegate {

    private View doneButton;
    private EditText nameTextView;
    private EditText descriptionTextView;
    private BackupImageView avatarImage;
    private AvatarDrawable avatarDrawable;
    private AvatarUpdater avatarUpdater;
    private ProgressDialog progressDialog;
    private TextSettingsCell typeCell;
    private TextSettingsCell adminCell;

    private TLRPC.FileLocation avatar;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private int chatId;
    private TLRPC.InputFile uploadedAvatar;
    private boolean signMessages;

    private boolean createAfterUpload;
    private boolean donePressed;

    private final static int done_button = 1;

    public ChannelEditActivity(Bundle args) {
        super(args);
        avatarDrawable = new AvatarDrawable();
        avatarUpdater = new AvatarUpdater();
        chatId = args.getInt("chat_id", 0);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        currentChat = MessagesController.getInstance().getChat(chatId);
        if (currentChat == null) {
            final Semaphore semaphore = new Semaphore(0);
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    currentChat = MessagesStorage.getInstance().getChat(chatId);
                    semaphore.release();
                }
            });
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (currentChat != null) {
                MessagesController.getInstance().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                MessagesStorage.getInstance().loadChatInfo(chatId, semaphore, false, false);
                try {
                    semaphore.acquire();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (info == null) {
                    return false;
                }
            }
        }
        avatarUpdater.parentFragment = this;
        avatarUpdater.delegate = this;
        signMessages = currentChat.signatures;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (avatarUpdater != null) {
            avatarUpdater.clear();
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(ir.besteveryeverapp.telegram.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    if (nameTextView.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(nameTextView, 2, 0);
                        return;
                    }
                    donePressed = true;

                    if (avatarUpdater.uploadingAvatar != null) {
                        createAfterUpload = true;
                        progressDialog = new ProgressDialog(getParentActivity());
                        progressDialog.setMessage(LocaleController.getString("Loading", ir.besteveryeverapp.telegram.R.string.Loading));
                        progressDialog.setCanceledOnTouchOutside(false);
                        progressDialog.setCancelable(false);
                        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", ir.besteveryeverapp.telegram.R.string.Cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                createAfterUpload = false;
                                progressDialog = null;
                                donePressed = false;
                                try {
                                    dialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                        progressDialog.show();
                        return;
                    }
                    if (!currentChat.title.equals(nameTextView.getText().toString())) {
                        MessagesController.getInstance().changeChatTitle(chatId, nameTextView.getText().toString());
                    }
                    if (info != null && !info.about.equals(descriptionTextView.getText().toString())) {
                        MessagesController.getInstance().updateChannelAbout(chatId, descriptionTextView.getText().toString(), info);
                    }
                    if (signMessages != currentChat.signatures) {
                        currentChat.signatures = true;
                        MessagesController.getInstance().toogleChannelSignatures(chatId, signMessages);
                    }
                    if (uploadedAvatar != null) {
                        MessagesController.getInstance().changeChatAvatar(chatId, uploadedAvatar);
                    } else if (avatar == null && currentChat.photo instanceof TLRPC.TL_chatPhoto) {
                        MessagesController.getInstance().changeChatAvatar(chatId, null);
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, ir.besteveryeverapp.telegram.R.drawable.ic_done, AndroidUtilities.dp(56));

        LinearLayout linearLayout;

        fragmentView = new ScrollView(context);
        fragmentView.setBackgroundColor(0xfff0f0f0);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle(LocaleController.getString("ChannelEdit", ir.besteveryeverapp.telegram.R.string.ChannelEdit));

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setBackgroundColor(0xffffffff);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout2.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable.setInfo(5, null, null, false);
        avatarDrawable.setDrawPhoto(true);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(64, 64, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 12));
        avatarImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                CharSequence[] items;

                if (avatar != null) {
                    items = new CharSequence[]{LocaleController.getString("FromCamera", ir.besteveryeverapp.telegram.R.string.FromCamera), LocaleController.getString("FromGalley", ir.besteveryeverapp.telegram.R.string.FromGalley), LocaleController.getString("DeletePhoto", ir.besteveryeverapp.telegram.R.string.DeletePhoto)};
                } else {
                    items = new CharSequence[]{LocaleController.getString("FromCamera", ir.besteveryeverapp.telegram.R.string.FromCamera), LocaleController.getString("FromGalley", ir.besteveryeverapp.telegram.R.string.FromGalley)};
                }

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == 0) {
                            avatarUpdater.openCamera();
                        } else if (i == 1) {
                            avatarUpdater.openGallery();
                        } else if (i == 2) {
                            avatar = null;
                            uploadedAvatar = null;
                            avatarImage.setImage(avatar, "50_50", avatarDrawable);
                        }
                    }
                });
                showDialog(builder.create());
            }
        });

        nameTextView = new EditText(context);
        if (currentChat.megagroup) {
            nameTextView.setHint(LocaleController.getString("GroupName", ir.besteveryeverapp.telegram.R.string.GroupName));
        } else {
            nameTextView.setHint(LocaleController.getString("EnterChannelName", ir.besteveryeverapp.telegram.R.string.EnterChannelName));
        }
        nameTextView.setMaxLines(4);
        nameTextView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setHintTextColor(0xff979797);
        nameTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        nameTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameTextView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        nameTextView.setFilters(inputFilters);
        AndroidUtilities.clearCursorDrawable(nameTextView);
        nameTextView.setTextColor(0xff212121);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 16 : 96, 0, LocaleController.isRTL ? 96 : 16, 0));
        nameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                avatarDrawable.setInfo(5, nameTextView.length() > 0 ? nameTextView.getText().toString() : null, null, false);
                avatarImage.invalidate();
            }
        });

        View lineView = new View(context);
        lineView.setBackgroundColor(0xffcfcfcf);
        linearLayout.addView(lineView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setBackgroundColor(0xffffffff);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        descriptionTextView = new EditText(context);
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        descriptionTextView.setHintTextColor(0xff979797);
        descriptionTextView.setTextColor(0xff212121);
        descriptionTextView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
        descriptionTextView.setBackgroundDrawable(null);
        descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        descriptionTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        descriptionTextView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(255);
        descriptionTextView.setFilters(inputFilters);
        descriptionTextView.setHint(LocaleController.getString("DescriptionOptionalPlaceholder", ir.besteveryeverapp.telegram.R.string.DescriptionOptionalPlaceholder));
        AndroidUtilities.clearCursorDrawable(descriptionTextView);
        linearLayout2.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 17, 12, 17, 6));
        descriptionTextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                    doneButton.performClick();
                    return true;
                }
                return false;
            }
        });
        descriptionTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        ShadowSectionCell sectionCell = new ShadowSectionCell(context);
        sectionCell.setSize(20);
        linearLayout.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentChat.megagroup || !currentChat.megagroup) {
            frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundColor(0xffffffff);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            typeCell = new TextSettingsCell(context);
            updateTypeCell();
            typeCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.list_selector);
            frameLayout.addView(typeCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            lineView = new View(context);
            lineView.setBackgroundColor(0xffcfcfcf);
            linearLayout.addView(lineView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

            frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundColor(0xffffffff);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            if (!currentChat.megagroup) {
                TextCheckCell textCheckCell = new TextCheckCell(context);
                textCheckCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.list_selector);
                textCheckCell.setTextAndCheck(LocaleController.getString("ChannelSignMessages", ir.besteveryeverapp.telegram.R.string.ChannelSignMessages), signMessages, false);
                frameLayout.addView(textCheckCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                textCheckCell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        signMessages = !signMessages;
                        ((TextCheckCell) v).setChecked(signMessages);
                    }
                });

                TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
                infoCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.greydivider);
                infoCell.setText(LocaleController.getString("ChannelSignMessagesInfo", ir.besteveryeverapp.telegram.R.string.ChannelSignMessagesInfo));
                linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            } else {
                adminCell = new TextSettingsCell(context);
                updateAdminCell();
                adminCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.list_selector);
                frameLayout.addView(adminCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                adminCell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bundle args = new Bundle();
                        args.putInt("chat_id", chatId);
                        args.putInt("type", 1);
                        presentFragment(new ChannelUsersActivity(args));
                    }
                });

                sectionCell = new ShadowSectionCell(context);
                sectionCell.setSize(20);
                linearLayout.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                if (!currentChat.creator) {
                    sectionCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.greydivider_bottom);
                }
            }
        }

        if (currentChat.creator) {
            frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundColor(0xffffffff);
            linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextSettingsCell textCell = new TextSettingsCell(context);
            textCell.setTextColor(0xffed3d39);
            textCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.list_selector);
            if (currentChat.megagroup) {
                textCell.setText(LocaleController.getString("DeleteMega", ir.besteveryeverapp.telegram.R.string.DeleteMega), false);
            } else {
                textCell.setText(LocaleController.getString("ChannelDelete", ir.besteveryeverapp.telegram.R.string.ChannelDelete), false);
            }
            frameLayout.addView(textCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            textCell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    if (currentChat.megagroup) {
                        builder.setMessage(LocaleController.getString("MegaDeleteAlert", ir.besteveryeverapp.telegram.R.string.MegaDeleteAlert));
                    } else {
                        builder.setMessage(LocaleController.getString("ChannelDeleteAlert", ir.besteveryeverapp.telegram.R.string.ChannelDeleteAlert));
                    }
                    builder.setTitle(LocaleController.getString("AppName", ir.besteveryeverapp.telegram.R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", ir.besteveryeverapp.telegram.R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
                            if (AndroidUtilities.isTablet()) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats, -(long) chatId);
                            } else {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                            }
                            MessagesController.getInstance().deleteUserFromChat(chatId, MessagesController.getInstance().getUser(UserConfig.getClientUserId()), info);
                            finishFragment();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", ir.besteveryeverapp.telegram.R.string.Cancel), null);
                    showDialog(builder.create());
                }
            });

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
            infoCell.setBackgroundResource(ir.besteveryeverapp.telegram.R.drawable.greydivider_bottom);
            if (currentChat.megagroup) {
                infoCell.setText(LocaleController.getString("MegaDeleteInfo", ir.besteveryeverapp.telegram.R.string.MegaDeleteInfo));
            } else {
                infoCell.setText(LocaleController.getString("ChannelDeleteInfo", ir.besteveryeverapp.telegram.R.string.ChannelDeleteInfo));
            }
            linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        nameTextView.setText(currentChat.title);
        nameTextView.setSelection(nameTextView.length());
        if (info != null) {
            descriptionTextView.setText(info.about);
        }
        if (currentChat.photo != null) {
            avatar = currentChat.photo.photo_small;
            avatarImage.setImage(avatar, "50_50", avatarDrawable);
        } else {
            avatarImage.setImageDrawable(avatarDrawable);
        }
        FontManager.instance().setTypefaceImmediate(fragmentView);
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null) {
                    descriptionTextView.setText(chatFull.about);
                }
                info = chatFull;
                updateAdminCell();
                updateTypeCell();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int updateMask = (Integer) args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_CHANNEL) != 0) {
                updateTypeCell();
            }
        }
    }

    @Override
    public void didUploadedPhoto(final TLRPC.InputFile file, final TLRPC.PhotoSize small, final TLRPC.PhotoSize big) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                uploadedAvatar = file;
                avatar = small.location;
                avatarImage.setImage(avatar, "50_50", avatarDrawable);
                if (createAfterUpload) {
                    try {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    doneButton.performClick();
                }
            }
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        avatarUpdater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (avatarUpdater != null && avatarUpdater.currentPicturePath != null) {
            args.putString("path", avatarUpdater.currentPicturePath);
        }
        if (nameTextView != null) {
            String text = nameTextView.getText().toString();
            if (text != null && text.length() != 0) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (avatarUpdater != null) {
            avatarUpdater.currentPicturePath = args.getString("path");
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }

    private void updateTypeCell() {
        String type = currentChat.username == null || currentChat.username.length() == 0 ? LocaleController.getString("ChannelTypePrivate", ir.besteveryeverapp.telegram.R.string.ChannelTypePrivate) : LocaleController.getString("ChannelTypePublic", ir.besteveryeverapp.telegram.R.string.ChannelTypePublic);
        if (currentChat.megagroup) {
            typeCell.setTextAndValue(LocaleController.getString("GroupType", ir.besteveryeverapp.telegram.R.string.GroupType), type, false);
        } else {
            typeCell.setTextAndValue(LocaleController.getString("ChannelType", ir.besteveryeverapp.telegram.R.string.ChannelType), type, false);
        }

        if (currentChat.creator && (info == null || info.can_set_username)) {
            typeCell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chatId);
                    ChannelEditTypeActivity fragment = new ChannelEditTypeActivity(args);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                }
            });
            typeCell.setTextColor(0xff212121);
            typeCell.setTextValueColor(0xff2f8cc9);
        } else {
            typeCell.setOnClickListener(null);
            typeCell.setTextColor(0xffa8a8a8);
            typeCell.setTextValueColor(0xffa8a8a8);
        }
    }

    private void updateAdminCell() {
        if (adminCell == null) {
            return;
        }
        if (info != null) {
            adminCell.setTextAndValue(LocaleController.getString("ChannelAdministrators", ir.besteveryeverapp.telegram.R.string.ChannelAdministrators), String.format("%d", info.admins_count), false);
        } else {
            adminCell.setText(LocaleController.getString("ChannelAdministrators", ir.besteveryeverapp.telegram.R.string.ChannelAdministrators), false);
        }
    }
}
