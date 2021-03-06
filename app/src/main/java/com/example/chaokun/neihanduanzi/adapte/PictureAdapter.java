package com.example.chaokun.neihanduanzi.adapte;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.example.chaokun.neihanduanzi.R;
import com.example.chaokun.neihanduanzi.activity.ImageDetailActivity;
import com.example.chaokun.neihanduanzi.base.BaseActivity;
import com.example.chaokun.neihanduanzi.base.BaseFragment;
import com.example.chaokun.neihanduanzi.base.ConstantString;
import com.example.chaokun.neihanduanzi.base.MyApplication;
import com.example.chaokun.neihanduanzi.bean.CommentNumber;
import com.example.chaokun.neihanduanzi.bean.DataBase;
import com.example.chaokun.neihanduanzi.bean.Picture;
import com.example.chaokun.neihanduanzi.callback.LoadFinishCallBack;
import com.example.chaokun.neihanduanzi.callback.LoadResultCallBack;
import com.example.chaokun.neihanduanzi.fragment.PictureFragment;
import com.example.chaokun.neihanduanzi.utils.DataBaseCrete;
import com.example.chaokun.neihanduanzi.utils.FileUtil;
import com.example.chaokun.neihanduanzi.utils.GsonUtil;
import com.example.chaokun.neihanduanzi.utils.ImageLoadProxy;
import com.example.chaokun.neihanduanzi.utils.MyHttpUtils;
import com.example.chaokun.neihanduanzi.utils.NetWorkUtil;
import com.example.chaokun.neihanduanzi.utils.ShareUtil;
import com.example.chaokun.neihanduanzi.utils.String2TimeUtil;
import com.example.chaokun.neihanduanzi.utils.ToastUtils;
import com.example.chaokun.neihanduanzi.view.ShowMaxImageView;
import com.lidroid.xutils.exception.DbException;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class PictureAdapter extends RecyclerView.Adapter<PictureAdapter.PictureViewHolder> {

    private int page;
    private int lastPosition = -1;
    private ArrayList<Picture.CommentsBean> pictures;
    private Activity mActivity;
    private Picture.PictureType mType;
    private boolean isWifiConnected;
    private LoadResultCallBack mLoadCallBack;
    private LoadFinishCallBack mLoadFinisCallBack;
    private LoadFinishCallBack mSaveFileCallBack;
    private DataBaseCrete datacre;
    private int menu;

    public PictureAdapter(Activity activity, Picture.PictureType type,LoadResultCallBack loadCallBack,LoadFinishCallBack LoadFinisCallBack,int menu) {
        mActivity = activity;
        pictures = new ArrayList<>();
        this.mType=type;
        isWifiConnected = NetWorkUtil.isWifiConnected(mActivity);
        ImageLoadProxy.initImageLoader(activity);
        this.mLoadCallBack=loadCallBack;
        this.mLoadFinisCallBack = LoadFinisCallBack;
        this.menu = menu;
    }

    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(viewToAnimate.getContext(), R
                    .anim.item_bottom_in);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    @Override
    public void onViewDetachedFromWindow(PictureViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.card.clearAnimation();
    }

    public void loadFirst() throws DbException {
        page = 1;
        loadDataByNetworkType();
    }

    public void loadNextPage() throws DbException {
        page++;
        loadDataByNetworkType();
    }

    private void loadDataByNetworkType() throws DbException {

        if (NetWorkUtil.isNetWorkConnected(mActivity)) {
            //先加载缓存数据在加载网络数据
//            loadCache();
            //有网络加载网络数据
            loadData();
        } else {
            //无网络加载缓存数据
            loadCache();
        }
    }

    /**
     * 加载缓存数据
     */
    private void loadCache() throws DbException {
        if(datacre==null){
            datacre = new DataBaseCrete(mActivity);
        }
         DataBase db = datacre.findPage(page,menu);
        if(null!=db){
            String request = db.getRequest();
            String count = db.getCounts();
            String[] counts = count.split(",");
            loadRequestJson(request,counts);
        }
    }

    private void loadRequestJson(String json,String[] counts){
        Picture picture = GsonUtil.jsonToBean(json,Picture.class);
        List<Picture.CommentsBean> comments = picture.getComments();
        for (int i = 0; i <comments.size() ; i++) {
            comments.get(i).setComment_counts(Integer.parseInt(counts[i]));
        }

        pictures.addAll(comments);
        notifyDataSetChanged();

        mLoadCallBack.onSuccess();

    }
    private void loadData() {

        MyHttpUtils.activitySendHttpClientGet(Picture.getRequestUrl(mType,page), new RequestCallBack<String>() {
            @Override
            public void onSuccess(ResponseInfo<String> responseInfo) {
                Picture picture = GsonUtil.jsonToBean(responseInfo.result,Picture.class);
                if (page==1){
                    pictures.clear();
                }
                pictures.addAll(picture.getComments());

                //每次加载后缓存


                //获取评论
                getCommentNumber(responseInfo.result);
            }

            @Override
            public void onFailure(HttpException error, String msg) {
                ToastUtils.showLong(mActivity,"网络错误");
                mLoadFinisCallBack.loadFinish(null);
            }
        });

    }


    private void getCommentNumber(final String request){
       final StringBuilder sb = new StringBuilder();
        for (Picture.CommentsBean joke : pictures) {
            sb.append("comment-" + joke.getComment_ID() + ",");
        }

        MyHttpUtils.activitySendHttpClientGet(CommentNumber.getCommentCountsURL(sb.toString()), new RequestCallBack<String>() {
            @Override
            public void onSuccess(ResponseInfo<String> responseInfo) {
                try {
                    mLoadFinisCallBack.loadFinish(null);
                    JSONObject object = new JSONObject(responseInfo.result);
                    JSONObject res = object.getJSONObject("response");
                    String[] comment_IDs = getRequestUrl().split("\\=")[1].split("\\,");
                    ArrayList<CommentNumber> commentNumbers = new ArrayList<>();
                    for (String comment_ID : comment_IDs) {

                        if (!res.isNull(comment_ID)) {
                            CommentNumber commentNumber = new CommentNumber();
                            commentNumber.setComments(res.getJSONObject(comment_ID).getInt(CommentNumber.COMMENTS));
                            commentNumber.setThread_id(res.getJSONObject(comment_ID).getString(CommentNumber.THREAD_ID));
                            commentNumber.setThread_key(res.getJSONObject(comment_ID).getString(CommentNumber.THREAD_KEY));
                            commentNumbers.add(commentNumber);
                        } else {
                            //可能会出现没有对应id的数据的情况，为了保证条数一致，添加默认数据
                            commentNumbers.add(new CommentNumber("0", "0", 0));
                        }
                    }

                    String counts = "";
                    for (int i = 0; i <pictures.size() ; i++) {
                        pictures.get(i).setComment_counts(commentNumbers.get(i).getComments());
                        counts+=commentNumbers.get(i).getComments()+",";
                    }

                    //缓存数据,保存数据库
                    SaveDataBase(request,counts);

                }catch (Exception e){
                    e.printStackTrace();
                }
                notifyDataSetChanged();
                mLoadCallBack.onSuccess();
            }

            @Override
            public void onFailure(HttpException error, String msg) {
                ToastUtils.showErr(mActivity);
                mLoadCallBack.onError();
                mLoadFinisCallBack.loadFinish(null);
            }
        });
    }

    /**
     *  缓存数据,保存数据库
     * @param request
     * @param reque
     * @throws DbException
     */
    private void SaveDataBase(String request,String reque) throws DbException {
        datacre = new DataBaseCrete(mActivity);
        datacre.delete(page,menu);

        DataBase data = new DataBase();
        data.setId(page);
        data.setRequest(request);
        data.setPage(page);
        data.setCounts(reque);
        data.setMenuNumber(menu);
        datacre.sava(data);
    }
    @Override
    public int getItemCount() {
        return pictures.size();
    }

    @Override
    public void onBindViewHolder(final PictureViewHolder holder, int position) {
        final Picture.CommentsBean bean = pictures.get(position);
        String picUrl = bean.getPics().get(0);
        if (picUrl.endsWith(".gif")) {
            holder.img_gif.setVisibility(View.VISIBLE);
            //非WIFI网络情况下，GIF图只加载缩略图，详情页才加载真实图片
            if (!isWifiConnected) {
                picUrl = picUrl.replace("mw600", "small").replace("mw1200", "small").replace
                        ("large", "small");
            }
        } else {
            holder.img_gif.setVisibility(View.GONE);
        }
        holder.progress.setProgress(0);
        holder.progress.setVisibility(View.VISIBLE);

        ImageLoadProxy.displayImageList(picUrl, holder.img, R.mipmap.ic_loading_large_1, new
                        SimpleImageLoadingListener() {
                            @Override
                            public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                                super.onLoadingComplete(imageUri, view, loadedImage);
                                holder.progress.setVisibility(View.GONE);
                            }
                        },
                new ImageLoadingProgressListener() {
                    @Override
                    public void onProgressUpdate(String imageUri, View view, int current, int total) {
                        holder.progress.setProgress((int) (current * 100f / total));
                    }
                });
        if (TextUtils.isEmpty(bean.getText_content().trim())) {
            holder.tv_content.setVisibility(View.GONE);
        } else {
            holder.tv_content.setVisibility(View.VISIBLE);
            holder.tv_content.setText(bean.getText_content().trim());
        }

        holder.tv_author.setText(bean.getComment_author());
        holder.tv_time.setText(String2TimeUtil.dateString2GoodExperienceFormat(bean.getComment_date()));
        holder.tv_like.setText(bean.getVote_positive());
        holder.tv_unlike.setText(bean.getVote_negative());
        holder.tv_comment_count.setText(bean.getComment_counts()+"");

        holder.img_share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(mActivity)
                        .title(R.string.app_name)
                        .titleColor(MyApplication.COLOR_OF_DIALOG_CONTENT)
                        .items(R.array.picture_dialog)
                        .backgroundColor(mActivity.getResources().getColor(MyApplication.COLOR_OF_DIALOG))
                        .contentColor(MyApplication.COLOR_OF_DIALOG_CONTENT)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {

                                switch (which) {
                                    //分享
                                    case 0:
                                        ShareUtil.sharePicture(mActivity, bean.getPics().get(0));
                                        break;
                                    //保存
                                    case 1:
                                        FileUtil.savePicture(mActivity, bean
                                                .getPics().get(0),mSaveFileCallBack);
                                        break;
                                }
                            }
                        }) .show();
            }
        });

        holder.img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mActivity, ImageDetailActivity.class);

                intent.putExtra(ConstantString.DATA_IMAGE_AUTHOR, bean.getComment_author());
                intent.putExtra(ConstantString.DATA_IMAGE_URL, (String[])bean.getPics().toArray(new String[bean.getPics().size()]));
                intent.putExtra(ConstantString.DATA_IMAGE_ID, bean.getComment_ID());
                intent.putExtra(ConstantString.DATA_THREAD_KEY, "comment-" + bean.getComment_ID());

                if (bean.getPics().get(0).endsWith(".gif")) {
                    intent.putExtra(ConstantString.DATA_IS_NEED_WEBVIEW, true);
                }

                mActivity.startActivity(intent);
            }
        });

        setAnimation(holder.card, position);
    }

    @Override
    public PictureViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pic,parent,false);
        return new PictureViewHolder(view);
    }

    public void setmSaveFileCallBack(LoadFinishCallBack mSaveFileCallBack) {
        this.mSaveFileCallBack = mSaveFileCallBack;
    }

    public static class PictureViewHolder extends RecyclerView.ViewHolder {

        @InjectView(R.id.tv_author)
        TextView tv_author;
        @InjectView(R.id.tv_time)
        TextView tv_time;
        @InjectView(R.id.tv_content)
        TextView tv_content;
        @InjectView(R.id.tv_like)
        TextView tv_like;
        @InjectView(R.id.tv_unlike)
        TextView tv_unlike;
        @InjectView(R.id.tv_comment_count)
        TextView tv_comment_count;
        @InjectView(R.id.tv_unsupport_des)
        TextView tv_un_support_des;
        @InjectView(R.id.tv_support_des)
        TextView tv_support_des;

        @InjectView(R.id.img_share)
        ImageView img_share;
        @InjectView(R.id.img_gif)
        ImageView img_gif;
        @InjectView(R.id.img)
        ShowMaxImageView img;

        @InjectView(R.id.ll_comment)
        LinearLayout ll_comment;
        @InjectView(R.id.progress)
        ProgressBar progress;
        @InjectView(R.id.card)
        CardView card;

        public PictureViewHolder(View contentView) {
            super(contentView);
            ButterKnife.inject(this, contentView);
        }
    }
}