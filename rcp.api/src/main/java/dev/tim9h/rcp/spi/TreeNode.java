package dev.tim9h.rcp.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public class TreeNode<T> {

	private List<TreeNode<T>> children = new ArrayList<>();

	private TreeNode<T> parent;

	private T data;

	public TreeNode(T data) {
		this.data = data;
	}

	public TreeNode(T data, TreeNode<T> parent) {
		this.data = data;
		this.parent = parent;
	}

	public List<TreeNode<T>> getChildren() {
		return children;
	}

	public void setParent(TreeNode<T> parent) {
		this.parent = parent;
	}

	public TreeNode<T> add(T value) {
		var child = new TreeNode<T>(value);
		child.setParent(this);
		this.children.add(child);
		return child;
	}

	@SuppressWarnings("unchecked")
	public TreeNode<T> add(T... values) {
		for (var value : values) {
			var child = new TreeNode<T>(value);
			child.setParent(this);
			this.children.add(child);
		}
		return this;
	}

	public TreeNode<T> add(TreeNode<T> child) {
		child.setParent(this);
		this.children.add(child);
		return child;
	}

	public T get() {
		return this.data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public boolean isRoot() {
		return (this.parent == null);
	}

	public boolean isLeaf() {
		return children.isEmpty();
	}

	public void removeParent() {
		this.parent = null;
	}

	@Override
	public String toString() {
		return (String) data + (children.isEmpty() ? StringUtils.EMPTY : (StringUtils.SPACE + children));
	}

	public Stream<TreeNode<T>> stream() {
		return getChildren().stream();
	}

	public TreeNode<T> get(T value) {
		var optional = stream().filter(child -> child.get().equals(value)).findAny();
		return optional.isPresent() ? optional.get() : null;
	}

	public TreeNode<T> get(TreeNode<T> value) {
		return get(value.get());
	}

	public List<TreeNode<T>> getChildrenOfChild(T value) {
		return stream().filter(child -> child.get().equals(value)).flatMap(TreeNode<T>::stream).toList();
	}

	public List<TreeNode<T>> getChildrenOfChild(TreeNode<T> node) {
		return getChildrenOfChild(node.get());
	}

}