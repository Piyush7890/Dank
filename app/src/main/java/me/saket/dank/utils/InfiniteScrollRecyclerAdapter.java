package me.saket.dank.utils;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.functions.Consumer;
import me.saket.dank.R;
import me.saket.dank.ui.subreddits.SubredditActivity;
import timber.log.Timber;

/**
 * Contains a header progress View for indicating fresh data load and a footer progress View
 * for indicating more data load. Both header and footer offer error states.
 *
 * @param <T> Type of items in the wrapped adapter.
 */
public class InfiniteScrollRecyclerAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder>
    implements Consumer<List<T>>
{

  private static final int VIEW_TYPE_HEADER = 20;
  private static final int VIEW_TYPE_FOOTER = 21;

  private RecyclerViewArrayAdapter<T, VH> wrappedAdapter;
  private HeaderMode activeHeaderMode = HeaderMode.HIDDEN;
  private FooterMode activeFooterMode = FooterMode.HIDDEN;
  private View.OnClickListener onHeaderErrorRetryClickListener;
  private View.OnClickListener onFooterErrorRetryClickListener;

  public enum HeaderMode {
    PROGRESS,
    ERROR,
    HIDDEN,

    /**
     * This is only used in {@link SubredditActivity}, where newly fetched submissions are held
     * until the user selects to save them. We shall use OOM in the future instead of using
     * enums here.
     */
    NEW_ITEMS_DOWNLOADED
  }

  public enum FooterMode {
    PROGRESS,
    ERROR,
    HIDDEN
  }

  public static <T, VH extends RecyclerView.ViewHolder> InfiniteScrollRecyclerAdapter<T, VH> wrap(RecyclerViewArrayAdapter<T, VH> adapterToWrap) {
    return new InfiniteScrollRecyclerAdapter<>(adapterToWrap);
  }

  private InfiniteScrollRecyclerAdapter(RecyclerViewArrayAdapter<T, VH> adapterToWrap) {
    this.wrappedAdapter = adapterToWrap;
    setHasStableIds(adapterToWrap.hasStableIds());

    wrappedAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override
      public void onChanged() {
        notifyDataSetChanged();
      }

      @Override
      public void onItemRangeChanged(int positionStart, int itemCount) {
        notifyItemRangeChanged(positionStart + getVisibleHeaderItemCount(), itemCount);
      }

      @Override
      public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
        notifyItemRangeChanged(positionStart + getVisibleHeaderItemCount(), itemCount, payload);
      }

      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        notifyItemRangeInserted(positionStart + getVisibleHeaderItemCount(), itemCount);
      }

      @Override
      public void onItemRangeRemoved(int positionStart, int itemCount) {
        notifyItemRangeRemoved(positionStart + getVisibleHeaderItemCount(), itemCount);
      }

      @Override
      public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
        // No notifyItemRangeMoved()? :/
        notifyItemRangeChanged(fromPosition + getVisibleHeaderItemCount(), toPosition + getVisibleHeaderItemCount() + itemCount);
      }
    });
  }

  @Override
  public void accept(List<T> items) {
    wrappedAdapter.updateData(items);
  }

  public void setHeaderMode(HeaderMode headerMode) {
    if (activeHeaderMode == headerMode) {
      return;
    }
    activeHeaderMode = headerMode;
    notifyDataSetChanged();
  }

  public void setOnHeaderErrorRetryClickListener(View.OnClickListener listener) {
    onHeaderErrorRetryClickListener = listener;
  }

  public void setFooterMode(FooterMode footerMode) {
    if (activeFooterMode == footerMode) {
      return;
    }
    activeFooterMode = footerMode;
    notifyDataSetChanged();
  }

  public void setOnFooterErrorRetryClickListener(View.OnClickListener listener) {
    onFooterErrorRetryClickListener = listener;
  }

  public boolean isWrappedAdapterItem(int position) {
    return !(isHeaderItem(position) || isFooterItem(position));
  }

  public T getItemInWrappedAdapter(int position) {
    return wrappedAdapter.getItem(position - getVisibleHeaderItemCount());
  }

  @Override
  public int getItemViewType(int position) {
    if (isHeaderItem(position)) {
      return VIEW_TYPE_HEADER;

    } else if (isFooterItem(position)) {
      return VIEW_TYPE_FOOTER;

    } else {
      int wrappedItemType = wrappedAdapter.getItemViewType(position - getVisibleHeaderItemCount());
      if (wrappedItemType == VIEW_TYPE_HEADER || wrappedItemType == VIEW_TYPE_FOOTER) {
        throw new IllegalStateException("Use another viewType value");
      }
      return wrappedItemType;
    }
  }

  @Override
  public long getItemId(int position) {
    if (isHeaderItem(position)) {
      return VIEW_TYPE_HEADER;

    } else if (isFooterItem(position)) {
      return VIEW_TYPE_FOOTER;

    } else {
      return wrappedAdapter.getItemId(position - getVisibleHeaderItemCount());
    }
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_HEADER) {
      // Create a proxy click listener so that retry works even if the retry click listener is registered after the error occurs.
      View.OnClickListener proxyRetryClickListener = v -> onHeaderErrorRetryClickListener.onClick(v);
      return HeaderViewHolder.create(LayoutInflater.from(parent.getContext()), parent, proxyRetryClickListener);

    } else if (viewType == VIEW_TYPE_FOOTER) {
      View.OnClickListener proxyRetryClickListener = v -> {
        Timber.i("Proxy click. onFooterErrorRetryClickListener: %s", onFooterErrorRetryClickListener);
        onFooterErrorRetryClickListener.onClick(v);
      };
      return FooterViewHolder.create(LayoutInflater.from(parent.getContext()), parent, proxyRetryClickListener);

    } else {
      return wrappedAdapter.onCreateViewHolder(parent, viewType);
    }
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
    switch (getItemViewType(position)) {
      case VIEW_TYPE_HEADER:
        ((HeaderViewHolder) holder).bind(activeHeaderMode);
        break;

      case VIEW_TYPE_FOOTER:
        ((FooterViewHolder) holder).bind(activeFooterMode);
        break;

      default:
        //noinspection unchecked
        wrappedAdapter.onBindViewHolder((VH) holder, position - getVisibleHeaderItemCount());
    }
  }

  @Override
  public int getItemCount() {
    return wrappedAdapter.getItemCount() + getVisibleHeaderItemCount() + getVisibleFooterItemCount();
  }

  private boolean isHeaderItem(int position) {
    return isHeaderVisible() && position == 0;
  }

  private boolean isFooterItem(int position) {
    return isFooterVisible() && position == getItemCount() - 1;
  }

  private int getVisibleHeaderItemCount() {
    return isHeaderVisible() ? 1 : 0;
  }

  private int getVisibleFooterItemCount() {
    return isFooterVisible() ? 1 : 0;
  }

  private boolean isHeaderVisible() {
    return activeHeaderMode != HeaderMode.HIDDEN;
  }

  private boolean isFooterVisible() {
    return activeFooterMode != FooterMode.HIDDEN;
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    @BindView(R.id.infinitescroll_header_progress_container) View progressContainer;
    @BindView(R.id.infinitescroll_header_error) TextView errorView;
    @BindView(R.id.infinitescroll_header_items_downloaded) TextView itemsDownloadedView;

    private View.OnClickListener retryClickListener;

    public static HeaderViewHolder create(LayoutInflater inflater, ViewGroup container, View.OnClickListener retryClickListener) {
      View progressItemView = inflater.inflate(R.layout.list_item_infinitescroll_header, container, false);
      return new HeaderViewHolder(progressItemView, retryClickListener);
    }

    public HeaderViewHolder(View itemView, View.OnClickListener retryClickListener) {
      super(itemView);
      this.retryClickListener = retryClickListener;
      ButterKnife.bind(this, itemView);
    }

    public void bind(HeaderMode headerMode) {
      progressContainer.setVisibility(headerMode == HeaderMode.PROGRESS ? View.VISIBLE : View.GONE);
      errorView.setVisibility(headerMode == HeaderMode.ERROR ? View.VISIBLE : View.GONE);
      itemsDownloadedView.setVisibility(headerMode == HeaderMode.NEW_ITEMS_DOWNLOADED ? View.VISIBLE : View.GONE);

      itemView.setOnClickListener(headerMode == HeaderMode.PROGRESS ? null : retryClickListener);
      itemView.setClickable(headerMode == HeaderMode.ERROR);
    }
  }

  static class FooterViewHolder extends RecyclerView.ViewHolder {
    @BindView(R.id.infinitescroll_footer_progress) View progressView;
    @BindView(R.id.infinitescroll_footer_error) View errorView;
    private View.OnClickListener retryClickListener;

    public static FooterViewHolder create(LayoutInflater inflater, ViewGroup container, View.OnClickListener retryClickListener) {
      View progressItemView = inflater.inflate(R.layout.list_item_infinitescroll_footer, container, false);
      return new FooterViewHolder(progressItemView, retryClickListener);
    }

    public FooterViewHolder(View itemView, View.OnClickListener retryClickListener) {
      super(itemView);
      this.retryClickListener = retryClickListener;
      ButterKnife.bind(this, itemView);
    }

    public void bind(FooterMode footerMode) {
      progressView.setVisibility(footerMode == FooterMode.ERROR ? View.GONE : View.VISIBLE);
      errorView.setVisibility(footerMode == FooterMode.ERROR ? View.VISIBLE : View.GONE);
      itemView.setOnClickListener(footerMode == FooterMode.ERROR ? retryClickListener : null);
      itemView.setClickable(footerMode == FooterMode.ERROR);
    }
  }
}
