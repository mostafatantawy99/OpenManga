/*
 * Copyright (C) 2016 Vasily Nikitin
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */

package org.nv95.openmanga.adapters;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.nv95.openmanga.R;
import org.nv95.openmanga.items.DownloadInfo;
import org.nv95.openmanga.items.ThumbSize;
import org.nv95.openmanga.services.DownloadService;
import org.nv95.openmanga.utils.ImageUtils;
import org.nv95.openmanga.utils.LayoutUtils;

/**
 * Created by nv95 on 03.01.16.
 */
public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadHolder>
        implements ServiceConnection, DownloadService.OnProgressUpdateListener, View.OnClickListener {

    private final Intent mIntent;
    @Nullable
    private DownloadService.DownloadBinder mBinder;
    private final RecyclerView mRecyclerView;

    public DownloadsAdapter(RecyclerView recyclerView) {
        mIntent = new Intent(recyclerView.getContext(), DownloadService.class);
        mBinder = null;
        mRecyclerView = recyclerView;
    }

    public void enable() {
        mRecyclerView.getContext().bindService(mIntent, this, 0);
    }

    public void disable() {
        if (mBinder != null) {
            mBinder.removeListener(this);
        }
        mRecyclerView.getContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mBinder = (DownloadService.DownloadBinder) service;
        mBinder.addListener(this);
        notifyDataSetChanged();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mBinder = null;
        notifyDataSetChanged();
    }

    @Override
    public DownloadHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        DownloadHolder holder = new DownloadHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download, parent, false));
        holder.imageButtonRemove.setImageDrawable(LayoutUtils.getThemedIcons(parent.getContext(), R.drawable.ic_clear_dark)[0]);
        holder.imageButtonRemove.setOnClickListener(this);
        holder.imageButtonRemove.setTag(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(DownloadHolder holder, int position) {
        if (mBinder != null) {
            holder.fill(mBinder.getItem(position));
        }
    }

    @Override
    public int getItemCount() {
        return mBinder != null ? mBinder.getCount() : 0;
    }

    @Override
    public void onProgressUpdated(int position) {
        RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (mBinder != null && holder != null && holder instanceof DownloadHolder) {
            DownloadInfo item = mBinder.getItem(position);
            if (item.pos < item.max) {
                ((DownloadHolder) holder).updateProgress(
                        item.pos * 100 + item.getChapterProgressPercent(),
                        item.max * 100,
                        item.chaptersProgresses[item.pos],
                        item.chaptersSizes[item.pos],
                        item.chapters.get(item.pos).name
                );
            } else {
                notifyItemChanged(position);
            }
        }
    }

    @Override
    public void onDataUpdated() {
        notifyDataSetChanged();
    }

    public boolean isPaused() {
        return mBinder != null && mBinder.isPaused();
    }

    public void setTaskPaused(boolean paused) {
        if (mBinder != null) {
            mBinder.setPaused(paused);
        }
    }

    @Override
    public void onClick(View view) {
        Object tag = view.getTag();
        if (tag != null && tag instanceof DownloadHolder) {
            final DownloadHolder holder = (DownloadHolder) tag;
            new AlertDialog.Builder(view.getContext())
                    .setMessage(view.getContext().getString(R.string.download_unqueue_confirm, holder.mTextViewTitle.getText()))
                    .setPositiveButton(R.string.action_remove, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            int pos = holder.getAdapterPosition();
                            if (mBinder != null && mBinder.removeFromQueue(pos)) {
                                notifyItemRemoved(pos);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                    .show();
        }
    }

    static class DownloadHolder extends RecyclerView.ViewHolder {

        private final ImageView mImageView;
        private final TextView mTextViewTitle;
        private final TextView mTextViewSubtitle;
        private final TextView mTextViewState;
        private final TextView mTextViewPercent;
        private final ProgressBar mProgressBarPrimary;
        private final ProgressBar mProgressBarSecondary;
        final ImageView imageButtonRemove;

        DownloadHolder(View itemView) {
            super(itemView);
            mImageView = (ImageView) itemView.findViewById(R.id.imageView);
            mTextViewTitle = (TextView) itemView.findViewById(R.id.textView_title);
            mTextViewSubtitle = (TextView) itemView.findViewById(R.id.textView_subtitle);
            mTextViewState = (TextView) itemView.findViewById(R.id.textView_state);
            mProgressBarPrimary = (ProgressBar) itemView.findViewById(R.id.progressBar_primary);
            mProgressBarSecondary = (ProgressBar) itemView.findViewById(R.id.progressBar_secondary);
            mTextViewPercent = (TextView) itemView.findViewById(R.id.textView_percent);
            imageButtonRemove = (ImageView) itemView.findViewById(R.id.imageButtonRemove);
        }

        @SuppressLint("SetTextI18n")
        public void fill(DownloadInfo data) {
            mTextViewTitle.setText(data.name);
            ImageUtils.setThumbnail(mImageView, data.preview, ThumbSize.THUMB_SIZE_LIST);
            switch (data.state) {
                case DownloadInfo.STATE_IDLE:
                    mTextViewState.setText(R.string.queue);
                    imageButtonRemove.setVisibility(View.VISIBLE);
                    break;
                case DownloadInfo.STATE_FINISHED:
                    mTextViewState.setText(R.string.completed);
                    imageButtonRemove.setVisibility(View.GONE);
                    break;
                case DownloadInfo.STATE_RUNNING:
                    mTextViewState.setText(R.string.saving_manga);
                    imageButtonRemove.setVisibility(View.GONE);
                    break;
            }
            if (data.pos < data.max) {
                updateProgress(data.pos, data.max, data.chaptersProgresses[data.pos], data.chaptersSizes[data.pos],
                        data.chapters.get(data.pos).name);
            } else {
                mProgressBarPrimary.setProgress(mProgressBarPrimary.getMax());
                mProgressBarSecondary.setProgress(mProgressBarSecondary.getMax());
                mTextViewPercent.setText("100%");
                mTextViewSubtitle.setText(itemView.getContext().getString(R.string.chapters_total, data.max));
            }
        }

        /**
         *
         * @param tPos current chapter
         * @param tMax chapters count
         * @param cPos current page
         * @param cMax pages count
         * @param subtitle chapter name
         */
        @SuppressLint("SetTextI18n")
        public void updateProgress(int tPos, int tMax, int cPos, int cMax, String subtitle) {
            mProgressBarPrimary.setMax(tMax);
            mProgressBarPrimary.setProgress(tPos);
            mProgressBarSecondary.setMax(cMax);
            mProgressBarSecondary.setProgress(cPos);
            mTextViewSubtitle.setText(subtitle);
            mTextViewPercent.setText((tMax == 0 ? 0 : tPos * 100 / tMax) + "%");
        }
    }
}
