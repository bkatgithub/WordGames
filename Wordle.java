package self.bk.projects.selenium.wordle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

//import common.DateHelper;
//import common.Helper;

/**
 * Standalone program that launches NYT Wordle and tries to
 * solve it.
 * 
 * When the program starts, a popup might show up. This popup
 * needs to be closed manually and then the program will continue.
 * 
 * The program tries words alphabetically. Words with repeated 
 * letters are tried when other options are exhausted.
 * 
 * @author bk
 *
 */

public class Wordle {

	// first word to try
	private static String firstWord = "audio";  

	private static WebDriver driver = null;
	private static boolean runHeadless = false;
	private static Map<String, WebElement> mKeys = new HashMap<>();
	private static String answer = "";
	private static int attemptNum = 0;
	private static String validLetters = "abcdefghijklmnopqrstuvwxyz";
	private static int correctCount = 0;
	private static int tryCount = 0;
	private static List<String> alDictionary = new ArrayList<>();
	private static List<String> alLettersPresent = new ArrayList<>();
	private static List<String> alLettersAbsent = new ArrayList<>();
	private static List<String> alRejectedWords = new ArrayList<>();
	private static String[] arrNotInPos = new String[] {"", "", "", "", ""};
	private static Map<String, Integer> mLetterCount = new HashMap<>();
	
	// Set these values for your setup. The program keeps a list of valid
	// words that are rejected by Wordle so that it does not try them again.
	private static String wordleWordsFile = "/Users/bk/eclipse-workspace/projects/src/main/java/self/bk/projects/selenium/wordle/wordlelist.txt";
	private static String invalidWordsFile = "/Users/bk/eclipse-workspace/projects/src/main/java/self/bk/projects/selenium/wordle/rejected_words.txt";
	
	public static void main(String[] args) throws IOException {
		new Wordle();
	}
	
	public Wordle() throws IOException {
		readWordList();
		
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		
		driver = runHeadless ? new ChromeDriver(options) : new ChromeDriver();
		driver.get("https://www.nytimes.com/games/wordle/index.html");
		
		List<WebElement> buttons = driver.findElements(By.tagName("button"));
		for (WebElement b : buttons) {
			if (b.getText().equals("Play")) {
				b.click();
				break;
			}
		}
		
		sleep(2000);
		
		buttons = driver.findElements(By.tagName("button"));
		for (WebElement b : buttons) {
			String label = b.getAttribute("aria-label");
			if (label != null && "Close".equals(label)) {
				b.click();
				break;
			}
		}
		
		sleep(2000);
		
		buttons = driver.findElements(By.tagName("button"));
		for (WebElement b : buttons) {
			String label = b.getAttribute("data-key");
			if (label != null) {
				int asciiVal = getAsciiVal(label);
				if (asciiVal == 8592) {label = "backspace";}
				else if (asciiVal == 8629) {label = "enter";}
				
				mKeys.put(label, b);
			}
		}

		for (int c=97; c<=122; c++) {
			char ch = (char) c;
			String str = ch + "";
			mLetterCount.put(str, 0);
		}
		
		sleep(2000);
//		int today = Integer.parseInt(DateHelper.getToday().replaceAll("-", "").substring(4));
		int wordIdx = 0;
		
		while (! tryWord(firstWord)) {
			firstWord = alDictionary.get(wordIdx++);
		}
		
		while (correctCount < 5 && tryCount < 6) {
			System.err.println("tryCount: " + tryCount + ", answer: " + answer + ", " + alLettersPresent);
			tryNextWord();
		}
		
		sleep(2000);
		
		driver.close();
		driver.quit();
	}
	
	/**
	 * Read words from provided files.
	 * 
	 * @throws FileNotFoundException
	 */
	private void readWordList() throws FileNotFoundException {
		Scanner scanner = new Scanner(new File(invalidWordsFile));
		while (scanner.hasNext()) {
			String word = scanner.nextLine().trim();
			alRejectedWords.add(word);
		}
		scanner.close();
		
		scanner = new Scanner(new File(wordleWordsFile));
		int count = 0;
		while (scanner.hasNext()) {
			++count;
			String word = scanner.nextLine().trim();
			if (word.matches("[" + validLetters + "]{5}")) {
				alDictionary.add(word);
			}
		}
		
		System.err.println("num valid words: " + alDictionary.size() + ", count: " + count);
		Collections.sort(alDictionary);
	}
	
	/**
	 * @param ignoreWordsWithRepeatedLetter
	 * @return
	 */
	private List<String> createWordMatchList(boolean ignoreWordsWithRepeatedLetter) {
		List<String> al = new ArrayList<>();
		
		for (String w : alDictionary) {
			if (alRejectedWords.contains(w)) {
				continue;
			}
			
			boolean possibleWord = true;
			boolean ignoreForRepeatedLetter = false;
			
			Map<String, Integer> mCount = getLetterCount(w);
			
			if (ignoreWordsWithRepeatedLetter) {
				for (String s : mCount.keySet()) {
					if (mCount.get(s) > 1 && (mLetterCount.get(s) == 0 || mLetterCount.get(s) == 1)) {
						ignoreForRepeatedLetter = true;
					}
				}
			}
			
			if (ignoreForRepeatedLetter) {
				continue;
			}
			
			for (String l : alLettersPresent) {
				if (! w.contains(l)) {
					possibleWord = false;
					alRejectedWords.add(w);
					break;
				}
			}
			
			if (possibleWord && w.matches(answer)) {
				al.add(w);
				break;  // exit since we only need one word to try
			}
		}
		
		return al;
	}
	
	/**
	 * @throws IOException 
	 * 
	 */
	private void tryNextWord() throws IOException {
		List<String> alMatchingWords = createWordMatchList(true);
		
		if (alMatchingWords.size() == 0) {
			System.err.println("No matches!");
			alMatchingWords = createWordMatchList(false);
		}
		
		tryWord(alMatchingWords.get(0));
	}
	
	/**
	 * Enter a word and read wordle's feedback.
	 * 
	 * @param word
	 * @throws IOException 
	 */
	private boolean tryWord(String word) throws IOException {
		// Enter the word
		String[] arr = word.split("");
		for (String c : arr) {
			mKeys.get(c).click();
		}
		
		mKeys.get("enter").click();
		
		sleep(3000);
		++attemptNum;
		System.err.println("Attempt " + attemptNum + ": " + word);
		
		// Set the row number that needs to be read
		String searchTarget = "Row " + attemptNum;
		List<String> alResult = new ArrayList<>();
		
		List<WebElement> divs = driver.findElement(By.id("wordle-app-game")).findElements(By.tagName("div"));
		for (WebElement div : divs) {
			String ariaLabel = div.getAttribute("aria-label");
			if (ariaLabel != null && searchTarget.equals(ariaLabel)) {
				List<WebElement> letterDivs = div.findElements(By.tagName("div"));
				
				// Read each letter.
				for (WebElement letterDiv : letterDivs) {
					String state = letterDiv.getAttribute("data-state");
					
					if (state != null) {
						// If this is not a valid word then delete it and add the word to the list of rejected words.
						if ("tbd".equals(state)) {
							alRejectedWords.add(word);
							deleteWord();
							updateRejectedWordList(word);
							--attemptNum;
							return false;
						}
						
						// Else take note of wordle's feedback for the letter.
						String letter = letterDiv.getText();
						alResult.add(state + " " + letter.toLowerCase());
					}
				}
			}
		}
		
		correctCount = 0;
		answer = "";
		
		// Based on wordle's feedback update the list of possible valid letters.
		updateValidLetters(alResult);
		String[] arrAnswer = new String[5];
		Map<String, Integer> mResult = new HashMap<>();
		
		for (int i=0; i<alResult.size(); i++) {
			String s = alResult.get(i);
			String letter = s.replaceAll(".* ", "");
			
			if (s.startsWith("correct ")) {
				++correctCount;
				arrAnswer[i] = letter;
				if (! mResult.containsKey(letter.toLowerCase())) {
					mResult.put(letter.toLowerCase(), 1);
				}
				else {
					mResult.put(letter.toLowerCase(), mResult.get(letter.toLowerCase()) + 1);
				}
			}
			else if (s.startsWith("present ")) {
				arrNotInPos[i] = arrNotInPos[i] + letter;
				arrAnswer[i] = validLetters;
				if (! mResult.containsKey(letter.toLowerCase())) {
					mResult.put(letter.toLowerCase(), 1);
				}
				else {
					mResult.put(letter.toLowerCase(), mResult.get(letter.toLowerCase()) + 1);
				}
			}
			else if (s.startsWith("absent ")) {
				arrAnswer[i] = validLetters;
			}
		}
		
		for (String letter : mResult.keySet()) {
			int letterCount = mResult.get(letter);
			if (mLetterCount.get(letter) < letterCount) {
				mLetterCount.put(letter, letterCount);
			}
		}
		
		for (int i=0; i<arrAnswer.length; i++) {
			String applicableLetters = arrAnswer[i];
			
			if (applicableLetters.length() == 1) {
				answer += applicableLetters;
			}
			else {
				String[] invalidLettersForPos = arrNotInPos[i].split("");

				for (String l : invalidLettersForPos) {
					applicableLetters = applicableLetters.replace(l, "");
				}
				answer += "[" + applicableLetters + "]";
			}
		}
		
		if (correctCount < 5) {
			alRejectedWords.add(word);
		}
		
		System.err.println("result: " + alResult + ", " + Arrays.asList(arrNotInPos));
		
		++tryCount;
		return true;
	}
	
	
	/**
	 * Based on feedback from wordle update the list of all possible valid letters.
	 * 
	 * @param alResult
	 */
	private void updateValidLetters(List<String> alResult) {
		for (int i=0; i<alResult.size(); i++) {
			String s = alResult.get(i);
			String letter = s.replaceAll(".* ", "");
			
			if (s.startsWith("absent ")) {
				if (! alLettersAbsent.contains(letter)) {
					alLettersAbsent.add(letter);
				}
			}
			else {
				if (! alLettersPresent.contains(letter) ) {
					alLettersPresent.add(letter);
				}
			}
			
			for (String c : alLettersPresent) {
				if (alLettersAbsent.contains(c)) {
					alLettersAbsent.remove(c);
				}
			}
			
			for (String c : alLettersAbsent) {
				validLetters = validLetters.replace(c, "");
			}
		}
	}
	
	/**
	 * Press backspace 5 times to delete a word in wordle.
	 */
	private void deleteWord() {
		for (int i=0; i<5; i++) {
			mKeys.get("backspace").click();
		}
	}
	
	@SuppressWarnings("unused")
	private void printKeys() {
		for (String k : mKeys.keySet()) {
			for (char c : k.toCharArray()) {
				System.err.println(k + " -- " + (int) c);
			}
		}
	}
	
	/**
	 * Get ascii val of a key - need this to identify enter and backspace keys.
	 * 
	 * @param key
	 * @return
	 */
	private int getAsciiVal(String key) {
		int val = -1;
		for (char c : key.toCharArray()) {
			val = (int) c;
		}
		return val;
	}
	
	/**
	 * Keep a list of words that wordle rejected.
	 * @param word
	 * @throws IOException
	 */
	private void updateRejectedWordList(String word) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(invalidWordsFile), true));
		bw.write(word + "\n");
		bw.close();
	}
	
	/**
	 * @param word
	 * @return
	 */
	private Map<String, Integer> getLetterCount(String word) {
		String[] arr = word.split("");
		Map<String, Integer> m = new HashMap<>();
		
		for (String s : arr) {
			if (! m.containsKey(s)) {
				m.put(s, 1);
			}
			else {
				m.put(s, m.get(s) + 1);
			}
		}
		
		return m;
	}
	
	/**
	 * @param ms
	 */
	private void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
