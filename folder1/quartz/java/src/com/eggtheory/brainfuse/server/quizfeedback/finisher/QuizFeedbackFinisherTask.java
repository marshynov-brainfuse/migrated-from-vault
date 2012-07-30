package com.eggtheory.brainfuse.server.quizfeedback.finisher;

import java.util.Date;

public class QuizFeedbackFinisherTask {
	public void markExpiredQuizFeedbacksAsDone() {
		System.out.println("QuizFeedbackFinisherTask: markExpiredQuizFeedbacksAsDone: "+new Date());
	}
}
