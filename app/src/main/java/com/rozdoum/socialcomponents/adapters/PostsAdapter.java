package com.rozdoum.socialcomponents.adapters;

import android.app.Activity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.rozdoum.socialcomponents.R;
import com.rozdoum.socialcomponents.activities.BaseActivity;
import com.rozdoum.socialcomponents.activities.MainActivity;
import com.rozdoum.socialcomponents.enums.ItemType;
import com.rozdoum.socialcomponents.managers.PostManager;
import com.rozdoum.socialcomponents.managers.ProfileManager;
import com.rozdoum.socialcomponents.managers.listeners.OnDataChangedListener;
import com.rozdoum.socialcomponents.managers.listeners.OnObjectChangedListener;
import com.rozdoum.socialcomponents.model.Post;
import com.rozdoum.socialcomponents.model.Profile;
import com.rozdoum.socialcomponents.utils.ImageUtil;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Kristina on 10/31/16.
 */

public class PostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final String TAG = PostsAdapter.class.getSimpleName();

    private List<Post> postList = new LinkedList<>();
    private ImageUtil imageUtil;
    private OnItemClickListener onItemClickListener;
    private MainActivity activity;
    private boolean isLoading = false;
    private boolean isMoreDataAvailable = true;
    private SwipeRefreshLayout swipeContainer;
    private ProfileManager profileManager;

    public PostsAdapter(final MainActivity activity, SwipeRefreshLayout swipeContainer) {
        this.activity = activity;
        this.swipeContainer = swipeContainer;
        imageUtil = ImageUtil.getInstance(activity.getApplicationContext());
        profileManager = ProfileManager.getInstance(activity.getApplicationContext());

        this.swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                onRefreshAction();
            }
        });
    }

    private void onRefreshAction() {
        if (activity.hasInternetConnection()) {
            loadFirstPage();
        } else {
            swipeContainer.setRefreshing(false);
            activity.showFloatButtonRelatedSnackBar(R.string.internet_connection_failed);
        }
    }

    private class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView postImageView;
        TextView titleTextView;
        TextView detailsTextView;
        TextView likeCounterTextView;
        TextView commentsCountTextView;
        ImageView authorImageView;

        ImageLoader.ImageContainer imageRequest;
        ImageLoader.ImageContainer authorImageRequest;

        PostViewHolder(View view) {
            super(view);

            postImageView = (ImageView) view.findViewById(R.id.postImageView);
            likeCounterTextView = (TextView) view.findViewById(R.id.likesCountTextView);
            commentsCountTextView = (TextView) view.findViewById(R.id.commentsCountTextView);
            titleTextView = (TextView) view.findViewById(R.id.titleTextView);
            detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
            authorImageView = (ImageView) view.findViewById(R.id.authorImageView);


            if (onItemClickListener != null) {
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onItemClickListener.onItemClick(getItemByPosition(getAdapterPosition()));
                    }
                });
            }
        }

        void bindData(Post post) {
            titleTextView.setText(post.getTitle());
            detailsTextView.setText(post.getDescription());
            likeCounterTextView.setText(String.valueOf(post.getLikesCount()));
            commentsCountTextView.setText(String.valueOf(post.getCommentsCount()));

            if (imageRequest != null) {
                imageRequest.cancelRequest();
            }

            String imageUrl = post.getImagePath();
            imageRequest = imageUtil.getImageThumb(imageUrl, postImageView, R.drawable.ic_stub, R.drawable.ic_stub);

            if (post.getAuthorId() != null) {
                authorImageView.setVisibility(View.VISIBLE);
                Object imageViewTag = authorImageView.getTag();

                if (!post.getAuthorId().equals(imageViewTag)) {
                    cancelLoadingAuthorImage(authorImageRequest);
                    authorImageView.setTag(post.getAuthorId());
                    profileManager.getProfile(post.getAuthorId(), createProfileChangeListener(authorImageView));
                }
            }
        }
    }

    private class LoadViewHolder extends RecyclerView.ViewHolder {
        LoadViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ItemType.ITEM.getTypeCode()) {
            return new PostViewHolder(inflater.inflate(R.layout.post_item_list_view, parent, false));
        } else {
            return new LoadViewHolder(inflater.inflate(R.layout.loading_view, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (position >= getItemCount() - 1 && isMoreDataAvailable && !isLoading) {
            isLoading = true;
            long lastItemCreatedDate = postList.get(postList.size() - 1).getCreatedDate();
            long nextItemCreatedDate = lastItemCreatedDate - 1;

            android.os.Handler mHandler = activity.getWindow().getDecorView().getHandler();
            mHandler.post(new Runnable() {
                public void run() {
                    //change adapter contents
                    postList.add(new Post(ItemType.LOAD));
                    notifyItemInserted(postList.size());
                }
            });

            loadNext(nextItemCreatedDate);
        }

        if (getItemViewType(position) != ItemType.LOAD.getTypeCode()) {
            ((PostViewHolder) holder).bindData(postList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    @Override
    public int getItemViewType(int position) {
        // TODO: 09.12.16 remove after clearing DB
        if (postList.get(position).getItemType() == null) {
            return ItemType.ITEM.getTypeCode();
        }

        return postList.get(position).getItemType().getTypeCode();
    }

    private Post getItemByPosition(int position) {
        return postList.get(position);
    }

    private void addList(List<Post> list) {
        this.postList.addAll(list);
        notifyDataSetChanged();
        isLoading = false;
    }

    public interface OnItemClickListener {
        void onItemClick(Post post);
    }

    public void loadFirstPage() {
        loadNext(0);
    }

    private void loadNext(final long nextItemCreatedDate) {
        if (!activity.hasInternetConnection()) {
            activity.showFloatButtonRelatedSnackBar(R.string.internet_connection_failed);
            hideProgress();
            return;
        }

        OnDataChangedListener<Post> onPostsDataChangedListener = new OnDataChangedListener<Post>() {
            @Override
            public void onListChanged(List<Post> list) {

                if (nextItemCreatedDate == 0) {
                    postList.clear();
                    swipeContainer.setRefreshing(false);
                }

                hideProgress();

                if (!list.isEmpty()) {
                    addList(list);
                    isMoreDataAvailable = true;
                } else {
                    isMoreDataAvailable = false;
                }
            }
        };

        PostManager.getInstance(activity).getPostsList(onPostsDataChangedListener, nextItemCreatedDate);
    }

    private void hideProgress() {
        if (!postList.isEmpty() && getItemViewType(postList.size() - 1) == ItemType.LOAD.getTypeCode()) {
            postList.remove(postList.size() - 1);
            notifyItemRemoved(postList.size() - 1);
        }
    }

    private void cancelLoadingAuthorImage(ImageLoader.ImageContainer authorImageRequest) {
        if (authorImageRequest != null) {
            authorImageRequest.cancelRequest();
        }
    }

    private OnObjectChangedListener<Profile> createProfileChangeListener(final ImageView authorImageView) {
        return new OnObjectChangedListener<Profile>() {
            @Override
            public void onObjectChanged(Profile obj) {
                if (obj.getPhotoUrl() != null) {
                    imageUtil.getImageThumb(obj.getPhotoUrl(),
                            authorImageView, R.drawable.ic_stub, R.drawable.ic_stub, true);
                }
            }
        };
    }
}
