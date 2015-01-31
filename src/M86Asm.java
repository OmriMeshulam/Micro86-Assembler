import java.util.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Scanner;

public class M86Asm {	
	
	private static int numOfMemory = 75;
	public static int [] memory = new int[numOfMemory];
	private final static String ZEROES = "0000";
	private final static String ZEROES2 = "00000000";
	public static int instructionRegister;
	public static int accumulator;
	public static int instructionPointer;
	public static boolean zeroFlag; // true when the operation resulted in 0
	public static boolean negFlag; // true when it resulted in a negative value.
	public static String fileName;
	public static boolean trace = false;
	public static boolean dump = false;
	public static boolean m86 = false;
	public static boolean cpp = false;
	public static Scanner myScanner = new Scanner(System.in);
	public static Map<Integer, String> opCodeTable = new TreeMap<Integer, String>();
	public static Map<Integer, String> varOperandTable = new TreeMap<Integer, String>();
	public static Map<Integer, Integer> intOperandTable = new TreeMap<Integer, Integer>();
	public static Map<String, Integer> varTable = new TreeMap<String, Integer>();
	public static Map<Integer, String> varPlaceTable = new TreeMap<Integer, String>();
	public static Map<String, Integer> varPlaceTableReverse = new TreeMap<String, Integer>();
	public static Map<Integer, String> commentTable = new TreeMap<Integer, String>(); // key == line number that it should be on 
	public static Map<Integer, String> jumpPlaceTable = new TreeMap<Integer, String>();
	public static Map<String, Integer> jumpPlaceTableReverse = new TreeMap<String, Integer>();
	public static Map<Integer, String> varFinder = new TreeMap<Integer, String>();
	public static int index = 0;
	
		
	public static void main(String[] args) throws Exception {
		processCommandLine(args);

		bootUp();
		loader(fileName);
		fetchExecuteAll();
		
		processPostMortem();
	}
	
	static void processCommandLine(String [] args) {
		boolean sawAnError = false;
	
		for (String arg : args)
			if (arg.startsWith("-")) {
				if (arg.substring(1).equals("d"))
					dump = true;
				else if (arg.substring(1).equals("t"))
					trace = true;
				else if (arg.substring(1).equals("m"))
					m86 = true;
				else if (arg.substring(1).equals("c"))
					cpp = true;
				else {
					System.err.println("Unknown option " + arg);
					sawAnError = true;
				}
			}
			else
				fileName = arg;
	
		if (fileName == null) {		// filename MUST be present on command-line
			System.err.println("Missing filename");
			sawAnError = true;
		}
			
		if (sawAnError) {
			System.err.println("Usage: CommandLineProcessor {-d, -t, -m, -c} <filename>");
			System.exit(1);
			
		}
	}

	
	public static void bootUp(){
		System.out.println(header());

		accumulator = 0;
		instructionPointer = 0;
		zeroFlag = false;
		negFlag = false;
		
		for(int i = 0; i < numOfMemory; i++){
			memory[i] = 0;
		}
		
		instructionRegister = memory[0];
	}
	
	public static String header(){
		String header = "================================\n"
				+ "M86 Assembler version 1.0\n"
				+ "================================\n"
				+ "Executable file: " + fileName;
		return header;
	}

	public static void memoryDump(){
		System.out.println("---Memory---");
		String memoryIndex, value, memIndexPad, valuePad;
		for(int i = 0; i < numOfMemory; i++){
			memoryIndex = Integer.toString(i, 16);
			memIndexPad = memoryIndex.length() <= 10 ? ZEROES2.substring(memoryIndex.length()) + memoryIndex : memoryIndex;
			value = Integer.toHexString(memory[i]);
			valuePad = value.length() <= 10 ? ZEROES2.substring(value.length()) + value : value;
			System.out.println(memIndexPad.toUpperCase() + ": " + valuePad.toUpperCase());
		}
		System.out.println("----------");
	}
	
	public static String returnRegisters(){
		//	Registers acc: 00000000 ip: 00000000 flags: 00000000 ir: 00000000
		String acc = Integer.toHexString(accumulator);
		String accPad = acc.length() <= 10 ? ZEROES2.substring(acc.length()) + acc : acc;
		String reg = Integer.toHexString(instructionRegister);
		String regPad = reg.length() <= 10 ? ZEROES2.substring(reg.length()) + reg : reg;
		String ip = Integer.toHexString(instructionPointer);
		String ipPad = ip.length() <= 10 ? ZEROES2.substring(ip.length()) + ip : ip;
		String zFlag = Integer.toHexString(boolToInt(zeroFlag));
		String zFlagPad = zFlag.length() <= 10 ? ZEROES2.substring(zFlag.length()) + zFlag : zFlag;
		String nFlag = Integer.toHexString(boolToInt(negFlag));
		String nFlagPad = nFlag.length() <= 10 ? ZEROES2.substring(nFlag.length()) + nFlag : nFlag;
		String s = "Registers acc: " + accPad.toUpperCase() + " ip: " + ipPad.toUpperCase() + " zFlag: " + zFlagPad + " nFlag: " + nFlagPad.toUpperCase() + " ir: " +  regPad;
		return s;
	}
	
	public static void printRegisters(){
		System.out.println(returnRegisters());
	}
	
	// loads the file, parses it, and stores it in memory
	public static void loader(String fileStringName){
		File file = new File(fileStringName);
	    Scanner myScanner = null;
	    String wholeLine = "";
	    String parse = "";
	    String temp = "";
	    int tempInt = 0;
	    
	    try {
			myScanner = new Scanner(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.out.println("Eror: Filename \"" + fileStringName + "\" not found, exiting.");
			System.exit(0);
		}

	    while(myScanner.hasNextLine()){
	    	wholeLine = myScanner.nextLine();
	    	Scanner inlineScanner = new Scanner(wholeLine);
	    	newLine:
		    	while(inlineScanner.hasNext()){
		    		parse = inlineScanner.next(); //
		    		myLine:
			    		if(OpCode.opCodeTable.containsKey(parse)){ //if the first word/object matches an opCode
			    			temp = parse;
			    			if(temp.equals("HALT")||temp.equals("IN")||temp.equals("OUT")){ 
			    				opCodeTable.put(index, temp);
			    				intOperandTable.put(index, 0); //operand = 0000
			    				index++;
			    				break newLine;
			    				
			    			}else if(inlineScanner.hasNextInt()){	// if operand is a number
			    				tempInt = inlineScanner.nextInt(); //
			    				opCodeTable.put(index, temp);
			    				intOperandTable.put(index, tempInt);
			    				index++;
			    				
			    			}else{
			    				parse = inlineScanner.next(); // if operand is a variable
			    				opCodeTable.put(index, temp);
			    				varOperandTable.put(index, parse);
			    				index++;
			    			}
			    			
			    		}else if(parse.contentEquals("VAR")){
			    			parse = inlineScanner.next(); //the actual variable name
			    			tempInt = inlineScanner.nextInt(); // the value
			    			varPlaceTable.put(index, parse);
			    			varPlaceTableReverse.put(parse, index);
			    			varTable.put(parse, tempInt);
			    			index++;
			    		
			    		}else if(parse.startsWith(":")){ //line jump 
			    			String myStr = (String) parse.subSequence(1, parse.length()-1);
			    		    jumpPlaceTable.put(index, myStr);
			    		    jumpPlaceTableReverse.put(myStr, index);
			    		    break myLine;
			    			
			    		}else if(parse.startsWith(";")){ //comment
			    			commentTable.put(index, wholeLine);
			    			break newLine;
			    		}
		    	}
	    	inlineScanner.close();
	    }
	    
		String opCodetemp = "";
		int intOperandTemp = 0;
		String opCode ="";
		String operandCode = "";
		int irCode = 0;
		
		// storing into memory
		for (int i = 0; i<index;i++){ //creating 2 part hex memory system
			if (varOperandTable.containsKey(i)){ // if the operand is a variable
				opCodetemp = opCodeTable.get(i); //returns the variable. getting the operand based on the index - 1st part 
				opCode = Integer.toHexString(OpCode.opCodeTable.get(opCodetemp));
				
				if(!jumpPlaceTableReverse.containsKey(varOperandTable.get(i))){ //if its a regular variable
					intOperandTemp = varPlaceTableReverse.get(varOperandTable.get(i)); // 2nd part. variable resolution
					operandCode = Integer.toHexString(intOperandTemp);
					operandCode = operandCode.length() <= 4 ? ZEROES.substring(operandCode.length()) + operandCode : operandCode;
					irCode = Integer.parseInt(opCode + operandCode,16); // -Combining 1&2 making number
					memory[i] = irCode;
				}else{ // if it's a jump
					intOperandTemp = jumpPlaceTableReverse.get(varOperandTable.get(i));
					operandCode = Integer.toString(intOperandTemp);
					operandCode = operandCode.length() <= 4 ? ZEROES.substring(operandCode.length()) + operandCode : operandCode;
					irCode = Integer.parseInt(opCode + operandCode,16); // -Combining 1&2 making number
					memory[i] = irCode;
				}
			}
			else if(intOperandTable.containsKey(i)){ // if the operand is an integer
				opCode = Integer.toHexString(OpCode.opCodeTable.get(opCodeTable.get(i)));
				intOperandTemp = intOperandTable.get(i);
				operandCode = Integer.toHexString(intOperandTemp);
				operandCode = operandCode.length() <= 4 ? ZEROES.substring(operandCode.length()) + operandCode : operandCode;
				irCode = Integer.parseInt(opCode + operandCode,16);
   				memory[i] = irCode;	
			}
			else if(varPlaceTable.containsKey(i)){ //if index number is a variable
				intOperandTemp = varTable.get(varPlaceTable.get(i));
				operandCode = Integer.toHexString(intOperandTemp);
				operandCode = operandCode.length() <= 4 ? ZEROES.substring(operandCode.length()) + operandCode : operandCode;
				irCode = Integer.parseInt(operandCode,16);
				memory[i] = irCode;
			}
			else {
				System.out.println("***System Error***");
				System.exit(1);
			}
		}
		// converting to decimal
		String test = "";
		for(int i=0;i<index;i++){
			test = Integer.toString(memory[i]);
			if(extractOpCode(memory[i])!=0)
				memory[i] = Integer.parseInt(test);
		}

		dissasembler();
		if (trace) tracing();
		processFileOutputs();
		
	}
	
	public static int boolToInt(boolean b) {
	    return b ? 1 : 0;
	}
	
	// takes an integer, and viewing it as an instruction, extracts the opcode (upper 16 bits) from it 
	public static int extractOpCode(int instruction){
		int opCode= instruction >>> 16;
		return opCode;
	}
	
	// takes an integer, and viewing it as an instruction, extracts the operand (lower 16 bits) from it 
	public static int extractOperand (int instruction){
		int operand = instruction & 0x0000FFFF;
		return operand;
	}
	
	// accepts an opcode and returns the corresponding mnemonic
	public static String getMnemonic(int opCode){
		String sOp = "";
		// System Control 
		switch(opCode){
		case 0x0100: sOp = "HALT"; //Halt execution 
					 break;
		// Data Movement
		case 0x0202: sOp = "LOAD"; // Move word at specified address in memory into accumulator 
					break;
		case 0x0201: sOp = "LOADI"; // Move immediate value (stored in instruction) into accumulator 
					break;
		case 0x0302: sOp = "STORE";  // Move word in accumulator to specified address
					break;
		// Arithmetic
		case 0x0402: sOp = "ADD"; // Add word at specified address to accumulator, result stored in accumulator 
					break;
		case 0x0401: sOp = "ADDI"; // Add immediate value to accumulator 
					break;
		case 0x0502: sOp = "SUB"; // Subtract word at specified address from accumulator 
					break;
		case 0x0501: sOp = "SUBI"; // Subtract immediate value to accumulator 
					break;
		case 0x0602: sOp = "MUL"; // Multiply accumulator by word at specified address 
					break;
		case 0x0601: sOp = "MULI"; // Multiply accumulator by immediate value 
					break;
		case 0x0702: sOp = "DIV"; // Divide accumulator by word at specified address 
					break;
		case 0x0701: sOp = "DIVI"; // Divide accumulator by immediate value 
					break;
		case 0x0802: sOp = "MOD"; // Take remainder of accumulator divided by word at specified address 
			
		case 0x0801: sOp = "MODI"; // Take remainder of accumulator divided by immediate value 
					break;
		// Comparison 
		case 0x0902: sOp = "CMP"; // Compare accumulator to word at specified address 
					break;
		case 0x0901: sOp = "CMPI"; // Compare accumulator to immediate value 
					break;
		// Branching
		case 0x0A01: sOp = "JMPI"; // Jump to address contained in immediate value 
					break;
		case 0x0B01: sOp = "JEI"; // Jump on equal to address contained in immediate value 
					break;
		case 0x0C01: sOp = "JNEI"; // Jump on not equal to address contained in immediate value  
					break;
		case 0x0D01: sOp = "JLI"; // Jump on less than to address contained in immediate value 
					break;
		case 0x0E01: sOp = "JLEI"; // Jump on less than or equal to address contained in immediate value 
					break;
		case 0x0F01: sOp = "JGI"; // Jump on greater than to address contained in immediate value 
					break;
		case 0x1001: sOp = "JGEI"; // Jump on greater than or equal to address contained in immediate value 
					break;
		// Input/Output 
		case 0x1100: sOp = "IN"; // Input byte from input port to accumulator 
					break;
		case 0x1200: sOp = "OUT"; // Output byte from accumulator to output port 
					break;
		default:
			sOp = "UNKNOWN_OP_CODE_ERROR";
					break;
		}
		
		return sOp;
	}
	
	// goes through memory taking each word and uses the above methods to get the mnemonic and operand and prints it out
	public static void dissasembler(){
		System.out.println("\n===== Disassembled Code =====");
		for(int i = 0; i < numOfMemory; i++){
			String index = Integer.toHexString(i);
			String opCode = getMnemonic(Integer.valueOf(extractOpCode(memory[i])));
			String value = Integer.toHexString(extractOperand(memory[i]));
			String lineNum= index.length() <= 4 ? ZEROES2.substring(index.length()) + index : index;
			String valueNum= value.length() <= 4 ? ZEROES.substring(value.length()) + value : value;
	
			System.out.print(lineNum +": ");
			if(opCode == "HALT"){
				System.out.println(opCode);
				System.out.println("...");
				break;
			}else
				System.out.println(opCode + " " + valueNum.toUpperCase());
		}
	}
	
	public static void fetchExecuteAll(){
		complete:
			while(true){
				fetchExecute();
				if(getMnemonic(extractOpCode(instructionRegister)) == "HALT")//||
				   //getMnemonic(extractOpCode(instructionRegister)) == "UNKOWN_OP_CODE_ERROR"
				{
					break complete;
				}
			}
	}
	
	// fetches the word from memory whose address (index) is contained in the instruction pointer register, 
	// places the word into the instruction register, and then adds 1 to that register (to set up for the next fetch)
	public static void fetchExecute(){
		
		String opCode = getMnemonic(extractOpCode(memory[instructionPointer]));
		String operandCode, hexFormat;
		int vCode, place;
		instructionRegister = memory[instructionPointer]; 
		
		switch (Op.valueOf(opCode)){
			case LOAD:  // Move word at specified address in memory into accumulator 
						place = extractOperand(instructionRegister);  
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); 
						vCode = new BigInteger(operandCode,16).intValue();
						accumulator = vCode; 
						break;
			case STORE: operandCode = Integer.toString(extractOperand(instructionRegister));
						vCode = Integer.parseInt(operandCode);
						memory[vCode] = accumulator; // Move word in accumulator to specified address
						break;
			case HALT: printRegisters(); // Halt Execution
						memoryDump();
						return;
			case LOADI:  accumulator = extractOperand(instructionRegister); // Move immediate value (stored in instruction) into accumulator 
						break;			
			case ADD:  // Add word at specified address to accumulator, result stored in accumulator
						place = extractOperand(instructionRegister);  
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); // Move word at specified address in memory into accumulator 
						vCode = new BigInteger(operandCode,16).intValue();
						accumulator +=  vCode; 
						break;
			case ADDI: accumulator += extractOperand(instructionRegister); // Add immediate value to accumulator 
						break;
			case SUB:	place = extractOperand(instructionRegister);  // Subtract word at specified address from accumulator 
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); 
						vCode = new BigInteger(operandCode,16).intValue();
						accumulator -=  vCode; 
						break;
			case SUBI: accumulator -= extractOperand(instructionRegister); // Subtract immediate value to accumulator 
						break;
			case MUL: 	place = extractOperand(instructionRegister); // Multiply accumulator by word at specified address 
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); 
						vCode = new BigInteger(operandCode,16).intValue();
						accumulator *=  vCode; 
						break;
			case MULI: accumulator *= extractOperand(instructionRegister); // Multiply accumulator by immediate value 
						break;
			case DIV: 	// Divide accumulator by word at specified address 
						place = extractOperand(instructionRegister);  
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); 
						vCode = new BigInteger(operandCode,16).intValue();
						if(vCode == 0){
							accumulator = 0;
						}else
						
							accumulator /=  vCode; 
							
								//}catch(ArithmeticException e){
									//accumulator = 0;
	//	}
						break;
			case DIVI: accumulator /= extractOperand(instructionRegister); // Divide accumulator by immediate value
						break;
			case MOD: // Take remainder of accumulator divided by word at specified address 
						place = extractOperand(instructionRegister);  
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); 
						vCode = new BigInteger(operandCode,16).intValue();
						accumulator %=  vCode; 
						break;
			case MODI: //try{
							accumulator %= extractOperand(instructionRegister); // Take remainder of accumulator divided by immediate value 
						//}catch(ArithmeticException e){
						//	accumulator=0;
						//}
						break;
			case CMP:   // Compare accumulator to word at specified address 
						place = extractOperand(instructionRegister);  
						hexFormat = Integer.toHexString(place);
						vCode = Integer.parseInt(hexFormat,16);
						operandCode = Integer.toHexString(memory[place]); 
						vCode = new BigInteger(operandCode,16).intValue();
				
						if((accumulator - vCode)>0){ 
							zeroFlag=false;
							negFlag=false;
						}else if (accumulator - vCode ==0){
							zeroFlag = true;
							negFlag=false;
						}else{
							zeroFlag=false;
							negFlag = true;
						}
						break;
			case CMPI: if(accumulator - extractOperand(instructionRegister)>0){ // Compare accumulator to immediate value
							zeroFlag=false;
							negFlag=false;
						}else if (accumulator - extractOperand(instructionRegister)==0){
							zeroFlag = true;
							negFlag=false;
						}else{
							zeroFlag=false;
							negFlag = true;
						}
						break;
			case JMPI: instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister)));  // Jump to address contained in immediate value 
						instructionRegister = memory[instructionPointer]; 
						if(trace)printRegisters();
						return;
			case JEI: if(zeroFlag){  // Jump on equal to address contained in immediate value
							instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister))); 
							instructionRegister = memory[instructionPointer];							
							if(trace)printRegisters();
							return;
						}
						break;
			case JNEI: if(!zeroFlag){ // Jump on not equal to address contained in immediate value  
							instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister))); 
							instructionRegister = memory[instructionPointer];
							if(trace)printRegisters();
							return;
						}
						break;
			case JLI: if(negFlag){ // Jump on less than to address contained in immediate value 
							instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister))); 
							instructionRegister = memory[instructionPointer];
							if(trace)printRegisters();
							return;
						}
						break;
			case JLEI: if(zeroFlag || negFlag){ // Jump on less than or equal to address contained in immediate value 
							instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister))); 
							instructionRegister = memory[instructionPointer];
							if(trace)printRegisters();
							return;
						}
						break;
			case JGI: if(!zeroFlag && !negFlag){ // Jump on greater than to address contained in immediate value 
							instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister))); 
							instructionRegister = memory[instructionPointer];
							if(trace)printRegisters();
							return;
						}
						break;
			case JGEI: if(zeroFlag || (!zeroFlag && !negFlag)){ //Jump on greater than or equal to address contained in immediate value
							instructionPointer = Integer.parseInt(Integer.toHexString(extractOperand(instructionRegister)));  
							instructionRegister = memory[instructionPointer];
							if(trace)printRegisters();
							return;
						}
						break;
			case IN:  accumulator = myScanner.nextInt(); // Input byte from input port to accumulator 
						break;
			case OUT: System.out.print(accumulator);// Output byte from accumulator to output port 
						break;
		}				
		if(trace)printRegisters();
		memoryDump();
		instructionPointer++;
	}
	
	// takes the instruction from the instruction register, extracts both the opcode and operand (i.e., you are decoding the instruction)
	public static String instructionExtractor(){
		String opCode = getMnemonic(extractOpCode(instructionRegister));
		String value = Integer.toHexString(extractOperand(instructionRegister));
		String result = opCode + " " + value;
		return result;
	}
	
	public static void processFileOutputs(){
		if(cpp)cOut();
		if(m86)mOut();
	}
	
	public static void processPostMortem(){
		if(dump)postMortemDump();
	}
	
	public static void postMortemDump(){
		System.out.println("\n===== Post-Mortem Dump (normal termination) =====\n"
				+ "--------------------\n"
				+ returnRegisters());
		memoryDump();
	}
	public static void tracing(){
		System.out.println("===== Execution Trace =====");
		memoryDump();
	}
	
	public static void cOut(){
		String [] cFile = new String [12];
		cFile[0] = "#include <iostream>";
		cFile[1] = "#include <cstdlib>";
		cFile[2] = "\n";
		cFile[3] = "using namespace std;";
		cFile[4] = "\n";
		cFile[5] = "int main() {";
		cFile[6] = "\tint _acc, _cmp;"; // \t in from here
		cFile[7] = "\tchar _byte;";
		cFile[8] = "\n";
		cFile[9] = "\tcin >> noskipws;";
		cFile[10] = "\n";
		cFile[11] = "\t//----------- Declarations ------------";
		File file = new File(fileName.substring(0, fileName.length()-4)+".cpp");
        BufferedWriter output;
		try {
			output = new BufferedWriter(new FileWriter(file));
	        for(int i=0;i<12;i++){
		    	output.write(cFile[i]);
		        output.write(String.format("%n"));
		    }
	        
        	output.write(String.format("%n"));
	        for(int i=0;i<index;i++){
	        	if(varPlaceTable.containsKey(i)){
	        		output.write("\tint "+varPlaceTable.get(i)+" = "+varTable.get(varPlaceTable.get(i))+";");
	        		output.write(String.format("%n"));
	        	}
	        }
    		output.write(String.format("%n"));
	        output.write("\t//----------- Code ------------");
	        output.write(String.format("%n"));
	        
	        for(int i=0;i<index;i++){
        		output.write(String.format("%n"));
	        	if(commentTable.containsKey(i)){
	        		output.write("\t//"+commentTable.get(i).substring(1));
	        		output.write(String.format("%n"));

	        	}
	        	if(jumpPlaceTable.containsKey(i)){
	        		output.write(jumpPlaceTable.get(i)+": ");
	        	}
	        	if(opCodeTable.containsKey(i)){
	        		if(varOperandTable.containsKey(i)){ //if the operand is a variable
	    				if(!jumpPlaceTableReverse.containsKey(varOperandTable.get(i))){ //if its a regular variable
	    					switch(Op.valueOf(opCodeTable.get(i))){
		    	    			case LOAD:
		    	    				output.write("\t_acc = "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case LOADI:
		    	    				output.write("\t_acc = "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			case STORE:
		    	    				output.write("\t"+varOperandTable.get(i)+" = _acc;");
		    	    				break;
		    	    			case ADD:
		    	    				output.write("\t_acc += "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case ADDI:
		    	    				output.write("\t_acc += "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			case SUB:
		    	    				output.write("\t_acc -= "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case SUBI:
		    	    				output.write("\t_acc -= "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			case DIV:
		    	    				output.write("\t_acc /= "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case DIVI:
		    	    				output.write("\t_acc /= "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			case MUL:
		    	    				output.write("\t_acc *= "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case MULI:
		    	    				output.write("\t_acc *= "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			case MOD:
		    	    				output.write("\t_acc %= "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case MODI:
		    	    				output.write("\t_acc %= "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			case CMP:
		    	    				output.write("\t_cmp = _acc - "+varOperandTable.get(i)+";");
		    	    				break;
		    	    			case CMPI:
		    	    				output.write("\t_cmp = _acc - "+varPlaceTableReverse.get(varOperandTable.get(i))+";");
		    	    				break;
		    	    			default:
		    	    				System.out.println("System Error: something went wrong.\nExiting...\n...");
		    	    				System.exit(1);
		    							
	    					}
	    					
	    				}else//its a jump
	    				{
	    					switch(Op.valueOf(opCodeTable.get(i))){
	    					case JMPI:
	    	    				output.write("\tgoto "+varOperandTable.get(i)+";");
	    	    				break;
	    	    			case JEI:
	    	    				output.write("\tif (!_cmp) goto " + varOperandTable.get(i)+";");
	    	    				break;
	    	    			case JNEI:
	    	    				output.write("\tif (_cmp) goto " + varOperandTable.get(i)+";");
	    	    				break;
	    	    			case JLI:
	    	    				output.write("\tif (_cmp < 0) goto " + varOperandTable.get(i)+";");
	    	    				break;
	    	    			case JLEI:
	    	    				output.write("\tif (_cmp <= 0) goto " + varOperandTable.get(i)+";");
	    	    				break;
	    	    			case JGI:
	    	    				output.write("\tif (_cmp > 0) goto " + varOperandTable.get(i)+";");
	    	    				break;
	    	    			case JGEI:
	    	    				output.write("\tif (_cmp >= 0) goto " + varOperandTable.get(i)+";");
	    	    				break;
	    	    			default:
	    	    				System.out.println("System Error: something went wrong.\nExiting...\n...");
	    	    				System.exit(1);
	    					}
	    				}

	        		}else if(intOperandTable.containsKey(i)){ // if the operand is a digit
	    	        	switch(Op.valueOf(opCodeTable.get(i))){
	    	    			case LOADI:
	    	    				output.write("\t_acc = "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case ADDI:
	    	    				output.write("\t_acc += "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case SUBI:
	    	    				output.write("\t_acc -= "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case DIVI:
	    	    				output.write("\t_acc /= "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case MULI:
	    	    				output.write("\t_acc *= "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case MODI:
	    	    				output.write("\t_acc %= "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case CMPI:
	    	    				output.write("\t_cmp = _acc - "+intOperandTable.get(i)+";");
	    	    				break;
	    	    			case HALT:
	    	    				output.write("\texit(0);");
	    	    				break;
	    	    			case IN:
	    	    				output.write("\tcin >> _byte; _acc = (int)_byte;");
	    	    				break;
	    	    			case OUT:
	    	    				output.write("\tcout << (char)_acc;");
	    	    				break;
	    	    			default:
	    	    				System.out.println("System Error: something went wrong.\nExiting...\n...\n...");
	    	    				System.exit(1);
	    	    		}
	        		}
	        	}
	        }
	        
	        output.write("\t//------ Bypassed HALT");
	        output.write(String.format("%n"));
	        output.write(String.format("%n"));
	        output.write("\texit(1);");
			output.write(String.format("%n"));
	        output.write("}");
	        output.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("File could not be created");
		}

	}
	
	public static void mOut(){
		File file = new File(fileName.substring(0, fileName.length()-4)+".m86");
        BufferedWriter output;
			try {
				output = new BufferedWriter(new FileWriter(file));
				String value, valuePad;
				for(int i = 0; i < numOfMemory; i++){
					value = Integer.toHexString(memory[i]);
					valuePad = value.length() <= 10 ? ZEROES2.substring(value.length()) + value : value;
					output.write(valuePad.toUpperCase());
				}				
				output.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("File could not be created");
			}
	}

	public enum Op
	 {
		LOAD, LOADI, ADD, ADDI, SUB, SUBI, HALT, STORE, MUL, MULI, DIV, DIVI, MOD, MODI, CMP, CMPI,
	     JMPI, JEI, JNEI, JLI, JLEI, JGI, JGEI, IN, OUT;
	 }
}

