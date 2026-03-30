package com.neonide.studio.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.neonide.studio.R;
import com.neonide.studio.view.treeview.model.TreeNode;

import java.util.Locale;

import com.neonide.studio.app.utils.DisplayNameUtils;

/** View holder for file/directory nodes in AndroidTreeView. */
public class FileTreeNodeViewHolder extends TreeNode.BaseNodeViewHolder<FileTreeNodeViewHolder.FileItem> {

    /**
     * UI scale for the file tree rows (icons, text, indentation). This is separate from TwoDScrollView
     * scaling: it makes rows actually larger/smaller instead of scaling the whole canvas.
     */
    public static float UI_SCALE = 1.0f;

    /** Absolute path of the currently opened file. Used for highlighting in the tree. */
    public static volatile String SELECTED_FILE_PATH = null;

    public static class FileItem {
        public final String name;
        public final String path;
        public final boolean isDirectory;

        /** Set to true once directory children are added to the TreeNode. */
        public boolean childrenLoaded;

        public FileItem(String name, String path, boolean isDirectory) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
            this.childrenLoaded = !isDirectory;
        }
    }

    private ImageView arrowView;
    private ImageView iconView;

    /** Apply current {@link #UI_SCALE} to a row view inflated from R.layout.tree_file_node. */
    public static void applyScaleToRowView(Context context, View rowView, int level) {
        if (rowView == null) return;

        int baseDp = 8;
        int indentDp = 14;
        int paddingStartPx = dpToPxScaled(context, baseDp + (Math.max(1, level) - 1) * indentDp);
        rowView.setPadding(paddingStartPx, rowView.getPaddingTop(), rowView.getPaddingRight(), rowView.getPaddingBottom());

        ImageView arrow = rowView.findViewById(R.id.node_arrow);
        ImageView icon = rowView.findViewById(R.id.node_icon);
        TextView text = rowView.findViewById(R.id.node_text);

        int iconSizePx = dpToPxScaled(context, 20);
        if (arrow != null) {
            ViewGroup.LayoutParams lp = arrow.getLayoutParams();
            lp.width = iconSizePx;
            lp.height = iconSizePx;
            arrow.setLayoutParams(lp);
        }
        if (icon != null) {
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            lp.width = iconSizePx;
            lp.height = iconSizePx;
            icon.setLayoutParams(lp);
        }
        if (text != null) {
            float baseSp = 14f;
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseSp * UI_SCALE);
        }
    }

    private static int dpToPxScaled(Context context, int dp) {
        return (int) (dp * UI_SCALE * context.getResources().getDisplayMetrics().density);
    }

    public FileTreeNodeViewHolder(Context context) {
        super(context);
    }

    @Override
    public View createNodeView(TreeNode node, FileItem value) {
        final View view = LayoutInflater.from(context).inflate(R.layout.tree_file_node, null, false);

        // Apply indentation/icon/text scaling based on tree depth.
        int level = Math.max(1, node.getLevel());
        applyScaleToRowView(context, view, level);

        arrowView = view.findViewById(R.id.node_arrow);
        iconView = view.findViewById(R.id.node_icon);
        TextView textView = view.findViewById(R.id.node_text);

        // Highlight currently opened file.
        boolean selected = value != null && !value.isDirectory
            && SELECTED_FILE_PATH != null
            && SELECTED_FILE_PATH.equals(value.path);
        view.setBackgroundResource(selected ? R.drawable.file_tree_item_bg_selected : R.drawable.file_tree_item_bg);

        textView.setText(value != null ? DisplayNameUtils.INSTANCE.safeForUi(value.name, 200) : "");
        updateIcons(value, node.isExpanded());

        return view;
    }

    @Override
    public void toggle(boolean active) {
        // active = expanded
        FileItem item = (FileItem) mNode.getValue();

        // Keep highlight state up-to-date even when reusing cached views.
        View cached = getCachedView();
        if (cached != null && item != null && !item.isDirectory) {
            boolean selected = SELECTED_FILE_PATH != null && SELECTED_FILE_PATH.equals(item.path);
            // Background is set on the row view (first child inside wrapper).
            if (cached instanceof com.neonide.studio.view.treeview.view.TreeNodeWrapperView) {
                View row = ((com.neonide.studio.view.treeview.view.TreeNodeWrapperView) cached).getNodeContainer().getChildAt(0);
                if (row != null) row.setBackgroundResource(selected ? R.drawable.file_tree_item_bg_selected : R.drawable.file_tree_item_bg);
            }
        }

        updateIcons(item, active);
    }

    private void updateIcons(FileItem item, boolean expanded) {
        if (iconView == null) return;

        boolean isDir = item != null && item.isDirectory;

        if (isDir) {
            // Show arrow + folder icon
            if (arrowView != null) {
                arrowView.setVisibility(View.VISIBLE);
                arrowView.setImageResource(expanded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);
            }
            iconView.setImageResource(R.drawable.ic_folder);
        } else {
            // Hide arrow + show file icon
            if (arrowView != null) {
                arrowView.setVisibility(View.INVISIBLE);
            }
            iconView.setImageResource(getFileIcon(item != null ? item.name : null));
        }
    }

    private int getFileIcon(String fileName) {
        if (fileName == null) return R.drawable.ic_file_unknown;

        String nameLower = fileName.toLowerCase(Locale.ROOT);

        // Special cases
        if ("gradlew".equals(nameLower) || "gradlew.bat".equals(nameLower)) {
            return R.drawable.ic_terminal;
        }

        int dot = nameLower.lastIndexOf('.');
        if (dot <= 0 || dot == nameLower.length() - 1) {
            return R.drawable.ic_file_unknown;
        }

        String ext = nameLower.substring(dot + 1);
        switch (ext) {
            case "java":
            case "jar":
                return R.drawable.ic_language_java;
            case "kt":
                return R.drawable.ic_language_kotlin;
            case "kts":
                return R.drawable.ic_language_kts;
            case "xml":
                return R.drawable.ic_language_xml;
            case "gradle":
                return R.drawable.ic_gradle;
            case "json":
                return R.drawable.ic_language_json;
            case "properties":
                return R.drawable.ic_language_properties;
            case "apk":
                return R.drawable.ic_file_apk;
            case "txt":
            case "log":
                return R.drawable.ic_file_txt;
            case "cpp":
            case "h":
                return R.drawable.ic_language_cpp;
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "webp":
            case "bmp":
            case "svg":
                return R.drawable.ic_file_image;
            case "sh":
            case "bash":
            case "zsh":
            case "fish":
            case "cmd":
            case "bat":
                return R.drawable.ic_terminal;
            default:
                return R.drawable.ic_file_unknown;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
