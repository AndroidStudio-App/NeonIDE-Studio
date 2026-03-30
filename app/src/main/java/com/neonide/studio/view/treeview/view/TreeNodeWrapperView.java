package com.neonide.studio.view.treeview.view;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.neonide.studio.R;

/**
 * Created by Bogdan Melnychuk on 2/10/15.
 */
public class TreeNodeWrapperView extends LinearLayout {
    private LinearLayout nodeItemsContainer;
    private ViewGroup nodeContainer;
    private final int containerStyle;

    public TreeNodeWrapperView(Context context, int containerStyle) {
        super(context);
        this.containerStyle = containerStyle;
        init();
    }

    private void init() {
        setOrientation(LinearLayout.VERTICAL);

        nodeContainer = new RelativeLayout(getContext());
        // Use WRAP_CONTENT width so TwoDScrollView can horizontally pan to reveal long names.
        nodeContainer.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        nodeContainer.setId(R.id.node_header);

        ContextThemeWrapper newContext = new ContextThemeWrapper(getContext(), containerStyle);
        nodeItemsContainer = new LinearLayout(newContext, null, containerStyle);
        // Use WRAP_CONTENT width so nested children also contribute to content width.
        nodeItemsContainer.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        nodeItemsContainer.setId(R.id.node_items);
        nodeItemsContainer.setOrientation(LinearLayout.VERTICAL);
        nodeItemsContainer.setVisibility(View.GONE);

        addView(nodeContainer);
        addView(nodeItemsContainer);
    }


    public void insertNodeView(View nodeView) {
        nodeContainer.addView(nodeView);
    }

    public ViewGroup getNodeContainer() {
        return nodeContainer;
    }
}
