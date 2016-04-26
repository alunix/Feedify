package fr.do_f.rssfeedify.main.feed.fragment;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import fr.do_f.rssfeedify.R;
import fr.do_f.rssfeedify.Utils;
import fr.do_f.rssfeedify.api.RestClient;
import fr.do_f.rssfeedify.api.json.feeds.FeedResponse;
import fr.do_f.rssfeedify.api.json.feeds.FeedResponse.*;
import fr.do_f.rssfeedify.api.json.feeds.article.ReadArticleResponse;
import fr.do_f.rssfeedify.api.json.menu.GetFeedResponse.*;
import fr.do_f.rssfeedify.broadcast.NetworkReceiver;
import fr.do_f.rssfeedify.main.feed.activity.DetailsActivity;
import fr.do_f.rssfeedify.main.feed.adapter.FeedAdapter;
import fr.do_f.rssfeedify.main.feed.adapter.SectionFeedAdapter;
import fr.do_f.rssfeedify.main.feed.listener.EndlessRecyclerViewScrollListener;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FeedFragment extends Fragment
        implements SwipeRefreshLayout.OnRefreshListener,
        FeedAdapter.onItemClickListener,
        SectionFeedAdapter.onSwitchCheckedListener,
        NetworkReceiver.onNetworkStateChanged {

    private static final String     TAG = "FeedFragment";
    private static final String     ARG_TYPE = "type";
    private static final String     ARG_FEED = "feed";

    @Bind(R.id.rvFeed)
    RecyclerView            rvFeed;

    @Bind(R.id.feed_swipe)
    SwipeRefreshLayout      swipe;

    private NetworkReceiver network;
    private int             networkState;

    private boolean         rvFeedInit;

    private List<Articles>  articles;
    private FeedAdapter     mAdapter;
    private String          token;

    private String          type;
    private Feed            feed;


    public FeedFragment() {
        // Required empty public constructor
    }

    public static FeedFragment newInstance(String type, Feed feed) {
        FeedFragment fragment = new FeedFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putSerializable(ARG_FEED, feed);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE);
            if (type.equals(Utils.FEEDBYID))
                feed = (Feed) getArguments().getSerializable(ARG_FEED);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.main_feed_fragment_feed, container, false);
        ButterKnife.bind(this, v);
        token = getActivity()
                .getSharedPreferences(Utils.SP, Context.MODE_PRIVATE)
                .getString(Utils.TOKEN, "null");
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        swipe.setOnRefreshListener(this);
        swipe.setColorSchemeResources(
                android.R.color.holo_blue_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_green_light,
                android.R.color.holo_red_light);

        swipe.setRefreshing(true);
        network = new NetworkReceiver();
        network.setOnNetworkStateChanged(this);
        networkState = network.singleCheck(getActivity());
        setupFeed();
        initFeed();
    }

    // FIRST CALL FOR INIT FEED
    public void initFeed() {

//        if (mAdapter == null) {
//            Log.d(TAG, "mAdapter == null");
//        }
//        if (rvFeed == null) {
//            Log.d(TAG, "rvFeed == null");
//        }
//
//        if (rvFeed.getAdapter() == null) {
//            Log.d(TAG, "get Adapter == null");
//        }

        if (networkState == NetworkReceiver.STATE_OFF)
        {
            String fileName = (feed != null) ? feed.getName() : "home";
            Type listType = new TypeToken<List<Articles>>() {}.getType();
            articles = Utils.read(getActivity(), fileName, listType);
            Log.d(TAG, "INIT FEED NETWORK OFF");
            if (articles == null)
            {
                Snackbar.make(getView(), "Error, can't retreive the feed", Snackbar.LENGTH_SHORT).show();
                return ;
            }
            mAdapter.refreshAdapter(articles, true);
        }
        else
        {
            Call<FeedResponse> call;
            if (type.equals(Utils.HOME)) {
                call = RestClient.get(token).getAllFeed(1);
            } else {
                call = RestClient.get(token).getAllFeedById(feed.getId(), 1);
            }

            Log.d(TAG, "TOKEN : "+token);

            call.enqueue(new Callback<FeedResponse>() {
                @Override
                public void onResponse(Call<FeedResponse> call, Response<FeedResponse> response) {
                    if (response.body() != null) {
                        Log.d(TAG, "setupfeed FIN "+response.body().getArticles().size());
                        articles = response.body().getArticles();
                        String fileName = (feed != null) ? feed.getName() : "home";
                        Utils.write(getActivity(), articles, fileName);
                        mAdapter.refreshAdapter(articles, true);
                        swipe.setRefreshing(false);
                    } else {
                        Log.d(TAG, "setupFeed FAIL : "+response.code());
                        //initFeed();
                    }
                }

                @Override
                public void onFailure(Call<FeedResponse> call, Throwable t) {
                    Log.d(TAG, "setupFeed onFailure : "+t.getMessage());

                }
            });
        }
    }

    // INIT FEED + SECTION
    public void setupFeed() {
        LinearLayoutManager lm = new LinearLayoutManager(getActivity());
        rvFeed.setHasFixedSize(true);
        rvFeed.setLayoutManager(lm);
        mAdapter = new FeedAdapter();
        mAdapter.setOnItemClickListener(this);

        rvFeed.setAdapter(mAdapter);
        rvFeed.addOnScrollListener(new EndlessRecyclerViewScrollListener(lm) {
            @Override
            public void onLoadMore(int page, int totalItemsCount) {
                // TODO: REFRESH API CALL
                if (page == 0) {

                } else {
                    addPage(page+1);
                }
            }
        });
    }


    // Pull To Refresh CALL
    @Override
    public void onRefresh() {
        Call<FeedResponse> call;
        if (type.equals(Utils.HOME)) {
            call = RestClient.get(token).getAllFeed(1);
        } else {
            call = RestClient.get(token).getAllFeedById(feed.getId(), 1);
        }

        call.enqueue(new Callback<FeedResponse>() {
            @Override
            public void onResponse(Call<FeedResponse> call, Response<FeedResponse> response) {
                if (response.body() != null) {
                    Log.d(TAG, "refresh FIN "+response.body().getArticles().size());
                    mAdapter.refreshAdapter(response.body().getArticles(), true);
                    swipe.setRefreshing(false);
                }
            }

            @Override
            public void onFailure(Call<FeedResponse> call, Throwable t) {
                Log.d(TAG, "onFailure");

            }
        });
    }

    // Infinite Scroll View CALL
    public void addPage(final int page) {
        swipe.setRefreshing(true);
        Call<FeedResponse> call;
        if (type.equals(Utils.HOME)) {
            call = RestClient.get(token).getAllFeed(page);
        } else {
            call = RestClient.get(token).getAllFeedById(feed.getId(), page);
        }
        call.enqueue(new Callback<FeedResponse>() {
            @Override
            public void onResponse(Call<FeedResponse> call, Response<FeedResponse> response) {
                Log.d(TAG, "ADD PAGE "+ page);
                if (response.body() != null) {
                    mAdapter.refreshAdapter(response.body().getArticles(), false);
                    swipe.setRefreshing(false);
                }
            }

            @Override
            public void onFailure(Call<FeedResponse> call, Throwable t) {

            }
        });
    }

    // do_f Interface Recycler View onClick
    @Override
    public void onItemClick(Articles articles, View v) {
        //markArticleAsRead(articles);
        DetailsActivity.newActivity(getActivity(), v, articles);
    }


    private void markArticleAsRead(Articles articles) {
        Call<ReadArticleResponse> call = RestClient.get(token).readArticle(articles.getId());
        call.enqueue(new Callback<ReadArticleResponse>() {
            @Override
            public void onResponse(Call<ReadArticleResponse> call, Response<ReadArticleResponse> response) {
                if (response.body() != null) {
                    Log.d(TAG, "SUCCESS");
                } else {
                    Log.d(TAG, "PASSCCESS + "+response.code());
                }
            }

            @Override
            public void onFailure(Call<ReadArticleResponse> call, Throwable t) {
                Log.d(TAG, "onFailure : "+t.getMessage());
            }
        });

    }

    // do_f Interface Switch Section Adapter
    @Override
    public void onCheckedChanged(boolean isChecked) {
        if (feed != null)
            Log.d(TAG, "onCheckedChanged : "+feed.getName());
        else
            Log.d(TAG, "onCheckedChanged : HOME");
    }

    // do_f Interface on Network Change
    @Override
    public void onStateChange(int state) {
        if (state == NetworkReceiver.STATE_ON) {
            onRefresh();
            networkState = state;
        } else {
            networkState = state;
        }
    }
}
