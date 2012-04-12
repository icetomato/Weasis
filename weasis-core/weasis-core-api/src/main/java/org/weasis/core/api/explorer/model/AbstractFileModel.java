package org.weasis.core.api.explorer.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;

public class AbstractFileModel implements TreeModel, DataExplorerModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileModel.class);

    public static final String[] functions = { "get", "close" }; //$NON-NLS-1$ //$NON-NLS-2$
    public static final String NAME = "All Files"; //$NON-NLS-1$
    public static final TreeModelNode series = new TreeModelNode(1, 0, TagW.SubseriesInstanceUID);
    public static final TreeModelNode file = new TreeModelNode(2, 0, TagW.SubseriesInstanceUID);

    public static final ArrayList<TreeModelNode> modelStrucure = new ArrayList<TreeModelNode>(5);
    static {
        modelStrucure.add(root);
        modelStrucure.add(series);
        modelStrucure.add(file);
    }

    private final Tree<MediaSeriesGroup> model;
    private PropertyChangeSupport propertyChange = null;

    public AbstractFileModel() {
        model = new Tree<MediaSeriesGroup>(rootNode);
    }

    @Override
    public synchronized List<Codec> getCodecPlugins() {
        return BundleTools.CODEC_PLUGINS;
    }

    @Override
    public Collection<MediaSeriesGroup> getChildren(MediaSeriesGroup node) {
        return model.getSuccessors(node);
    }

    @Override
    public MediaSeriesGroup getHierarchyNode(MediaSeriesGroup parent, Object value) {
        if (parent != null || value != null) {
            synchronized (model) {
                for (MediaSeriesGroup node : model.getSuccessors(parent)) {
                    if (node.equals(value)) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void addHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        synchronized (model) {
            model.addLeaf(root, leaf);
        }
    }

    @Override
    public void removeHierarchyNode(MediaSeriesGroup root, MediaSeriesGroup leaf) {
        synchronized (model) {
            Tree<MediaSeriesGroup> tree = model.getTree(root);
            if (tree != null) {
                tree.removeLeaf(leaf);
            }
        }
    }

    @Override
    public MediaSeriesGroup getParent(MediaSeriesGroup node, TreeModelNode modelNode) {
        if (null != node && modelNode != null) {
            // synchronized (model) {
            // Tree<MediaSeriesGroup> tree = model.getTree(node);
            // if (tree != null) {
            // Tree<MediaSeriesGroup> parent = null;
            // while ((parent = tree.getParent()) != null) {
            // if (parent.getHead().getTagID().equals(modelNode.getTagElement())) {
            // return parent.getHead();
            // }
            // tree = parent;
            // }
            // }
            // }
        }
        return null;
    }

    public void dispose() {
        synchronized (model) {
            for (Iterator<MediaSeriesGroup> iterator = this.getChildren(TreeModel.rootNode).iterator(); iterator
                .hasNext();) {
                MediaSeriesGroup s = iterator.next();
                if (s instanceof Series) {
                    ((Series) s).dispose();
                }
            }
        }
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public List<TreeModelNode> getModelStructure() {
        return modelStrucure;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange == null) {
            propertyChange = new PropertyChangeSupport(this);
        }
        propertyChange.addPropertyChangeListener(propertychangelistener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertychangelistener) {
        if (propertyChange != null) {
            propertyChange.removePropertyChangeListener(propertychangelistener);
        }

    }

    @Override
    public void firePropertyChange(final ObservableEvent event) {
        if (propertyChange != null) {
            if (event == null) {
                throw new NullPointerException();
            }
            if (SwingUtilities.isEventDispatchThread()) {
                propertyChange.firePropertyChange(event);
            } else {
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        propertyChange.firePropertyChange(event);
                    }
                });
            }
        }
    }

    public void removeSeries(MediaSeriesGroup dicomSeries) {
        if (dicomSeries != null) {
            // remove first series in UI (Dicom Explorer, Viewer using this series)
            firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Remove, AbstractFileModel.this, null,
                dicomSeries));
            // remove in the data model
            MediaSeriesGroup studyGroup = getParent(dicomSeries, AbstractFileModel.root);
            removeHierarchyNode(studyGroup, dicomSeries);
            LOGGER.info("Remove Series: {}", dicomSeries); //$NON-NLS-1$
            dicomSeries.dispose();
        }
    }

    @Override
    public boolean applySplittingRules(Series original, MediaElement media) {
        return false;
    }

    public void get(String[] argv) throws IOException {

    }

    public void close(String[] argv) throws IOException {
        final String[] usage = { "Remove DICOM files in Dicom Explorer", //$NON-NLS-1$
            "Usage: dicom:close [series] [ARGS]", //$NON-NLS-1$
            "  -a --all Close all series", //$NON-NLS-1$
            "  -s --series <args>	Close series, [arg] is Series UID", "  -? --help		show help" }; //$NON-NLS-1$ //$NON-NLS-2$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || (args.isEmpty() && !opt.isSet("all"))) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, AbstractFileModel.this,
                    null, AbstractFileModel.this));
                if (opt.isSet("all")) { //$NON-NLS-1$
                    for (MediaSeriesGroup seriesGroup : model.getSuccessors(rootNode)) {
                        removeSeries(seriesGroup);
                    }
                } else if (opt.isSet("series")) { //$NON-NLS-1$
                    for (String seriesUID : args) {
                        MediaSeriesGroup series = getHierarchyNode(rootNode, seriesUID);
                        if (series instanceof Series) {
                            removeSeries(series);
                            break;
                        }
                    }
                }
            }
        });
    }

    @Override
    public TreeModelNode getTreeModelNodeForNewPlugin() {
        return file;
    }

}
