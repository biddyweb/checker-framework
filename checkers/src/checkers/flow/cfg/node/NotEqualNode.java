package checkers.flow.cfg.node;

import java.util.Collection;
import java.util.LinkedList;

import checkers.flow.util.HashCodeUtils;
import checkers.util.InternalUtils;

import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;

/**
 * A node for the not equal comparison:
 * 
 * <pre>
 *   <em>expression</em> != <em>expression</em>
 * </pre>
 * 
 * @author Stefan Heule
 * @author Charlie Garrett
 * 
 */
public class NotEqualNode extends Node {

    protected Tree tree;
    protected Node left;
    protected Node right;

    public NotEqualNode(Tree tree, Node left, Node right) {
        assert tree.getKind() == Kind.NOT_EQUAL_TO;
        this.tree = tree;
        this.type = InternalUtils.typeOf(tree);
        this.left = left;
        this.right = right;
    }

    public Node getLeftOperand() {
        return left;
    }

    public Node getRightOperand() {
        return right;
    }

    @Override
    public Tree getTree() {
        return tree;
    }

    @Override
    public <R, P> R accept(NodeVisitor<R, P> visitor, P p) {
        return visitor.visitNotEqual(this, p);
    }

    @Override
    public String toString() {
        return "(" + getLeftOperand() + " != " + getRightOperand() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof NotEqualNode)) {
            return false;
        }
        NotEqualNode other = (NotEqualNode) obj;
        return getLeftOperand().equals(other.getLeftOperand())
                && getRightOperand().equals(other.getRightOperand());
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hash(getLeftOperand(), getRightOperand());
    }

    @Override
    public Collection<Node> getOperands() {
        LinkedList<Node> list = new LinkedList<Node>();
        list.add(getLeftOperand());
        list.add(getRightOperand());
        return list;
    }
}