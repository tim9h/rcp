package dev.tim9h.rcp.cli.query.bean;

import com.google.gson.annotations.SerializedName;

public class InstantAnswer {

	@SerializedName("Abstract")
	private String abstractAnswer;

	@SerializedName("AbstractText")
	private String abstractText;

	@SerializedName("AbstractSource")
	private String abstractSource;

	@SerializedName("AbstractURL")
	private String abstractURL;

//	@SerializedName("Answer")
	private Answer answer;

	@SerializedName("AnswerType")
	private String answerType;

	@SerializedName("Definition")
	private String definition;

	@SerializedName("DefinitionSource")
	private String definitionSource;

	@SerializedName("DefinitionURL")
	private String definitionUrl;

	@SerializedName("Entity")
	private String entity;

	@SerializedName("Heading")
	private String heading;

	public String getAbstractAnswer() {
		return abstractAnswer;
	}

	public String getAbstractText() {
		return abstractText;
	}

	public String getAbstractSource() {
		return abstractSource;
	}

	public String getAbstractURL() {
		return abstractURL;
	}

	public Answer getAnswer() {
		return answer;
	}

	public String getAnswerType() {
		return answerType;
	}

	public String getDefinition() {
		return definition;
	}

	public String getDefinitionSource() {
		return definitionSource;
	}

	public String getDefinitionUrl() {
		return definitionUrl;
	}

	public String getEntity() {
		return entity;
	}

	public String getHeading() {
		return heading;
	}

}
