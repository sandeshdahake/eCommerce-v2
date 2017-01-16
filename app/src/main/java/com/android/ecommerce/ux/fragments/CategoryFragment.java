package com.android.ecommerce.ux.fragments;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.ShareCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.TransitionInflater;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.android.ecommerce.CONST;
import com.android.ecommerce.MyApplication;
import com.android.ecommerce.R;
import com.android.ecommerce.SettingsMy;
import com.android.ecommerce.api.EndPoints;
import com.android.ecommerce.api.GsonRequest;
import com.android.ecommerce.entities.Metadata;
import com.android.ecommerce.entities.SortItem;
import com.android.ecommerce.entities.drawerMenu.DrawerItemCategory;
import com.android.ecommerce.entities.drawerMenu.DrawerItemSubCategory;
import com.android.ecommerce.entities.filtr.Filters;
import com.android.ecommerce.entities.productList.Product;
import com.android.ecommerce.entities.productList.ProductList;
import com.android.ecommerce.entities.productList.ProductListResponse;
import com.android.ecommerce.entities.productList.Summary;
import com.android.ecommerce.interfaces.CategoryRecyclerInterface;
import com.android.ecommerce.interfaces.FilterDialogInterface;
import com.android.ecommerce.listeners.OnSingleClickListener;
import com.android.ecommerce.utils.EndlessRecyclerScrollListener;
import com.android.ecommerce.utils.MsgUtils;
import com.android.ecommerce.utils.RecyclerMarginDecorator;
import com.android.ecommerce.ux.MainActivity;
import com.android.ecommerce.ux.adapters.ProductsRecyclerAdapter;
import com.android.ecommerce.ux.adapters.SortSpinnerAdapter;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import timber.log.Timber;

/**
 * Fragment handles various types of product lists.
 * Also allows displaying the search results.
 */
public class CategoryFragment extends Fragment {

    private static final String TYPE = "type";
    private static final String CATEGORY_NAME = "categoryName";
    private static final String CATEGORY_ID = "categoryId";
    private static final String SEARCH_QUERY = "search_query";

    /**
     * Prevent the sort selection callback during initialization.
     */
    private boolean firstTimeSort = true;

    private View loadMoreProgress;

    private long categoryId;
    private String categoryType;

    /**
     * Search string. The value is set only if the fragment is launched in order to searching.
     */
    private String searchQuery = null;

    /**
     * Request metadata containing URLs for endlessScroll.
     */
    private Summary productsMetadata;

    private ImageSwitcher switchLayoutManager;
    private Spinner sortSpinner;

    // Content specific
    private TextView emptyContentView;
    private RecyclerView productsRecycler;
    private GridLayoutManager productsRecyclerLayoutManager;
    private ProductsRecyclerAdapter productsRecyclerAdapter;
    private EndlessRecyclerScrollListener endlessRecyclerScrollListener;

    // Filters parameters
    private Filters filters;
    private String filterParameters = null;
    private ImageView filterButton;

    // Properties used to restore previous state
    private int toolbarOffset = -1;
    private boolean isList = false;
    private String subCategoryID;


    /**
     * Show product list defined by parameters.
     *
     * @param categoryId id of product category.
     * @param name       name of product list.
     * @return new fragment instance.
     */
    public static CategoryFragment newInstance(long categoryId, String name) {
        Bundle args = new Bundle();
        args.putLong(CATEGORY_ID, categoryId);
        args.putString(CATEGORY_NAME, name);
        args.putString(SEARCH_QUERY, null);
        CategoryFragment fragment = new CategoryFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Show product list populated from drawer menu.
     *
     * @param drawerItemCategory corresponding drawer menu item.
     * @return new fragment instance.
     */
    public static CategoryFragment newInstance(DrawerItemSubCategory drawerItemCategory) {
        if (drawerItemCategory != null)
            return newInstance(drawerItemCategory.getId(), drawerItemCategory.getName());
        else {
            Timber.e(new RuntimeException(), "Creating category with null arguments");
            return null;
        }
    }

    /**
     * Show product list based on search results.
     *
     * @param searchQuery word for searching.
     * @return new fragment instance.
     */
    public static CategoryFragment newInstance(String searchQuery) {
        Bundle args = new Bundle();
        args.putString(SEARCH_QUERY, searchQuery);

        CategoryFragment fragment = new CategoryFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Timber.d("%s - onCreateView", this.getClass().getSimpleName());
        View view = inflater.inflate(R.layout.fragment_category, container, false);

        this.emptyContentView = (TextView) view.findViewById(R.id.category_products_empty);
        this.loadMoreProgress = view.findViewById(R.id.category_load_more_progress);
        this.sortSpinner = (Spinner) view.findViewById(R.id.category_sort_spinner);
        this.switchLayoutManager = (ImageSwitcher) view.findViewById(R.id.category_switch_layout_manager);

        Bundle startBundle = getArguments();
        if (startBundle != null) {
            categoryId = startBundle.getLong(CATEGORY_ID, 0);
            String categoryName = startBundle.getString(CATEGORY_NAME, "");
            categoryType = startBundle.getString(TYPE, "category");
            searchQuery = startBundle.getString(SEARCH_QUERY, null);
            boolean isSearch = false;
            if (searchQuery != null && !searchQuery.isEmpty()) {
                isSearch = true;
                categoryId = -10;
                categoryName = searchQuery;
            }

            Timber.d("Category type: %s. CategoryId: %d. FilterUrl: %s.", categoryType, categoryId, filterParameters);

            AppBarLayout appBarLayout = (AppBarLayout) view.findViewById(R.id.category_appbar_layout);
            if (toolbarOffset != -1) appBarLayout.offsetTopAndBottom(toolbarOffset);
            appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
                @Override
                public void onOffsetChanged(AppBarLayout appBarLayout, int i) {
                    toolbarOffset = i;
                }
            });
            MainActivity.setActionBarTitle(categoryName);
            this.filterButton = (ImageView) view.findViewById(R.id.category_filter_button);
            filterButton.setOnClickListener(new OnSingleClickListener() {
                @Override
                public void onSingleClick(View view) {
                    if (filters == null) {
                        MsgUtils.showToast(getActivity(), MsgUtils.TOAST_TYPE_MESSAGE, getString(R.string.Filter_unavailable), MsgUtils.ToastLength.SHORT);
                    }

                    else {
                        FilterDialogFragment filterDialogFragment = FilterDialogFragment.newInstance(filters, new FilterDialogInterface() {
                            @Override
                            public void onFilterSelected(String newFilterUrl) {
                                filterParameters = newFilterUrl;
                                filterButton.setImageResource(R.drawable.filter_selected);
                                getProducts(null);
                            }

                            @Override
                            public void onFilterCancelled() {
                                filterParameters = null;
                                filterButton.setImageResource(R.drawable.filter_unselected);
                                getProducts(null);
                            }
                        });
                        if (filterDialogFragment != null)
                            filterDialogFragment.show(getFragmentManager(), "filterDialogFragment");
                        else {
                            MsgUtils.showToast(getActivity(), MsgUtils.TOAST_TYPE_INTERNAL_ERROR, null, MsgUtils.ToastLength.SHORT);
                        }
                    }

                }
            });


            if (filterParameters != null && !filterParameters.isEmpty()) {
                filterButton.setImageResource(R.drawable.filter_selected);
            } else {
                filterButton.setImageResource(R.drawable.filter_unselected);
            }

            // Opened first time (not form backstack)
            if (productsRecyclerAdapter == null || productsRecyclerAdapter.getItemCount() == 0) {
                prepareRecyclerAdapter();
                prepareProductRecycler(view);
                prepareSortSpinner();
                getProducts(null);

            } else {
                prepareProductRecycler(view);
                prepareSortSpinner();
                Timber.d("Restore previous category state. (Products already loaded) ");
            }
        } else {
            MsgUtils.showToast(getActivity(), MsgUtils.TOAST_TYPE_INTERNAL_ERROR, getString(R.string.Internal_error), MsgUtils.ToastLength.LONG);
            Timber.e(new RuntimeException(), "Run category fragment without arguments.");
        }
        return view;
    }


    /**
     * Prepare content recycler. Create custom adapter and endless scroll.
     *
     * @param view root fragment view.
     */
    private void prepareProductRecycler(View view) {
        this.productsRecycler = (RecyclerView) view.findViewById(R.id.category_products_recycler);
        productsRecycler.addItemDecoration(new RecyclerMarginDecorator(getActivity(), RecyclerMarginDecorator.ORIENTATION.BOTH));
        productsRecycler.setItemAnimator(new DefaultItemAnimator());
        productsRecycler.setHasFixedSize(true);
        switchLayoutManager.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                return new ImageView(getContext());
            }
        });
        if (isList) {
            switchLayoutManager.setImageResource(R.drawable.grid_off);
            productsRecyclerLayoutManager = new GridLayoutManager(getActivity(), 1);
        } else {
            switchLayoutManager.setImageResource(R.drawable.grid_on);
            // TODO A better solution would be to dynamically determine the number of columns.
            productsRecyclerLayoutManager = new GridLayoutManager(getActivity(), 2);
        }
        productsRecycler.setLayoutManager(productsRecyclerLayoutManager);
        endlessRecyclerScrollListener = new EndlessRecyclerScrollListener(productsRecyclerLayoutManager) {
            @Override
            public void onLoadMore(int currentPage) {
                Timber.e("Load more");
                if (productsMetadata != null && productsMetadata.getPageSize() != null && productsMetadata.getPageNumber() != null && currentPage>productsMetadata.getPageNumber() && productsMetadata.getTotalProducts()>= (productsMetadata.getPageNumber()* productsMetadata.getPageSize()) ) {
                    getProducts(currentPage);
                    //productsRecyclerAdapter.notifyDataSetChanged();
                } else {
                    Timber.d("CustomLoadMoreDataFromApi NO MORE DATA");
                }
            }
        };
        productsRecycler.setAdapter(productsRecyclerAdapter);
        productsRecycler.addOnScrollListener(endlessRecyclerScrollListener);

        switchLayoutManager.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                if (isList) {
                    isList = false;
                    switchLayoutManager.setImageResource(R.drawable.grid_on);
                    productsRecyclerAdapter.defineImagesQuality(false);
                    animateRecyclerLayoutChange(2);
                } else {
                    isList = true;
                    switchLayoutManager.setImageResource(R.drawable.grid_off);
                    productsRecyclerAdapter.defineImagesQuality(true);
                    animateRecyclerLayoutChange(1);
                }
            }
        });
    }

    private void prepareRecyclerAdapter() {
        productsRecyclerAdapter = new ProductsRecyclerAdapter(getActivity(), new CategoryRecyclerInterface() {
            @Override
            public void onProductSelected(View caller, Product product) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                    setReenterTransition(TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.fade));
                }
                ((MainActivity) getActivity()).onProductSelected(product.getId());
            }
        });
    }

    /**
     * Animate change of rows in products recycler LayoutManager.
     *
     * @param layoutSpanCount number of rows to display.
     */
    private void animateRecyclerLayoutChange(final int layoutSpanCount) {
        Animation fadeOut = new AlphaAnimation(1, 0);
        fadeOut.setInterpolator(new DecelerateInterpolator());
        fadeOut.setDuration(400);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                productsRecyclerLayoutManager.setSpanCount(layoutSpanCount);
                productsRecyclerLayoutManager.requestLayout();
                Animation fadeIn = new AlphaAnimation(0, 1);
                fadeIn.setInterpolator(new AccelerateInterpolator());
                fadeIn.setDuration(400);
                productsRecycler.startAnimation(fadeIn);
            }
        });
        productsRecycler.startAnimation(fadeOut);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Animation in = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in_slowed);
        Animation out = AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_out);
        switchLayoutManager.setInAnimation(in);
        switchLayoutManager.setOutAnimation(out);
    }

    private void prepareSortSpinner() {
        SortSpinnerAdapter sortSpinnerAdapter = new SortSpinnerAdapter(getActivity());
        sortSpinner.setAdapter(sortSpinnerAdapter);
        sortSpinner.setOnItemSelectedListener(null);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private int lastSortSpinnerPosition = -1;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (firstTimeSort) {
                    firstTimeSort = false;
                    return;
                }
                Timber.d("Selected pos: %d", position);

                if (position != lastSortSpinnerPosition) {
                    Timber.d("OnItemSelected change");
                    lastSortSpinnerPosition = position;
                    getProducts(null);
                } else {
                    Timber.d("OnItemSelected no change");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Timber.d("OnNothingSelected - no change");
            }
        });
    }


    /**
     * Endless content loader. Should be used after views inflated.
     *
     */
    private void getProducts(Integer pageNum) {
        String url;
        loadMoreProgress.setVisibility(View.VISIBLE);
        Bundle startBundle = getArguments();
        String categoryName = null;
        if (startBundle != null) {
            categoryName= startBundle.getString(CATEGORY_NAME, "");
        }
        if(pageNum == null){
            if (endlessRecyclerScrollListener != null) endlessRecyclerScrollListener.clean();
            productsRecyclerAdapter.clear();

        }
        SortItem sortItem = (SortItem) sortSpinner.getSelectedItem();

        url = String.format(EndPoints.PRODUCTS);
        url = url+ categoryName;
        // Build request url
        if (searchQuery != null) {
            String newSearchQueryString;
            try {
                newSearchQueryString = URLEncoder.encode(searchQuery, "UTF-8");
                url = url+ newSearchQueryString;
            } catch (UnsupportedEncodingException e) {
                Timber.e(e, "Unsupported encoding exception");
                newSearchQueryString = URLEncoder.encode(searchQuery);
            }
            Timber.d("GetFirstProductsInCategory isSearch: %s", searchQuery);
            // url += "?search=" + newSearchQueryString;
        }

        if (sortItem != null) {
            url = url + "?filters=sortby%3D" + sortItem.getValue();
        }else{
            url = url + "?filters=sortby%3D" + "popularity";
        }

        if (pageNum != null ) {
            url = url + "%26pageNumber=" + pageNum;
        }
        // Build request url
        if (searchQuery != null) {
            String newSearchQueryString;
            try {
                newSearchQueryString = URLEncoder.encode(searchQuery, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Timber.e(e, "Unsupported encoding exception");
                newSearchQueryString = URLEncoder.encode(searchQuery);
            }
            Timber.d("GetFirstProductsInCategory isSearch: %s", searchQuery);
           // url += "?search=" + newSearchQueryString;
        }

        // Add filters parameter if exist
        if (filterParameters != null && !filterParameters.isEmpty()) {
          //  url += filterParameters;
        }

        if(pageNum == null || pageNum >1){
            GsonRequest<ProductList> getProductsRequest = new GsonRequest<>(Request.Method.GET, url, null, ProductList.class,
                    new Response.Listener<ProductList>() {
                        @Override
                        public void onResponse(@NonNull ProductList response) {
                            firstTimeSort = false;
//                        Timber.d("response:" + response.toString());

                            productsRecyclerAdapter.addProducts(response.getProducts());
                            productsMetadata = response.getSummary();
                            checkEmptyContent();
                           // getFilters();
                            loadMoreProgress.setVisibility(View.GONE);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    if (loadMoreProgress != null) loadMoreProgress.setVisibility(View.GONE);
                    checkEmptyContent();
                    MsgUtils.logAndShowErrorMessage(getActivity(), error);
                }
            });
/*
        getProductsRequest.setRetryPolicy(MyApplication.getDefaultRetryPolice());
        getProductsRequest.setShouldCache(false);
*/
            MyApplication.getInstance().addToRequestQueue(getProductsRequest, CONST.CATEGORY_REQUESTS_TAG);

        }

    }

    private void checkEmptyContent() {
        if (productsRecyclerAdapter != null && productsRecyclerAdapter.getItemCount() > 0) {
            emptyContentView.setVisibility(View.INVISIBLE);
            productsRecycler.setVisibility(View.VISIBLE);
        } else {
            emptyContentView.setVisibility(View.VISIBLE);
            productsRecycler.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onStop() {
        if (loadMoreProgress != null) {
            // Hide progress dialog if exist.
            if (loadMoreProgress.getVisibility() == View.VISIBLE && endlessRecyclerScrollListener != null) {
                // Fragment stopped during loading data. Allow new loading on return.
                endlessRecyclerScrollListener.resetLoading();
            }
            loadMoreProgress.setVisibility(View.GONE);
        }
        MyApplication.getInstance().cancelPendingRequests(CONST.CATEGORY_REQUESTS_TAG);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (productsRecycler != null) productsRecycler.clearOnScrollListeners();
        super.onDestroyView();
    }
}
