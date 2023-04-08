package dev.tim9h.rcp.core.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javafx.scene.Group;
import javafx.scene.Scene;

@Singleton
public class InjectableScene extends Scene {

	@Inject
	public InjectableScene(Group group) {
		super(group);
	}

}
