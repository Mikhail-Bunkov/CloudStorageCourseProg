package bunkov.server.nettyHandlers;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import static command.Command.*;

public class CommandMessageHandler extends SimpleChannelInboundHandler<String> {
	String userName = "arteans"; //Пока так оставлю ,позже добавлю логирование

	public static final ConcurrentLinkedQueue<SocketChannel> channels = new ConcurrentLinkedQueue<>();
	private Path root = Path.of("cloud");
	private Path currentPath = root;

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Client connected: " + ctx.channel());
		channels.add((SocketChannel) ctx.channel());
	}
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
		System.out.println("Message from user: "+msg);
		String[] command = msg.split(" ");
		if(EXIT_COMMAND.equals(command[0])){
			ctx.close();
		}else if(AUTH.equals(command[0])){
//			authService(command[1],command[2]); // Здесь будет авторизация.
			Path rootPath = Path.of("cloud", userName);
			createUserDirectory(ctx, rootPath);
			ctx.writeAndFlush(ROOT+": "+ rootPath+" "/*+ getFileInfo(rootPath)*/);//Пока не особо понял как риализовать это, но все в процессе.
		}else if(TOUCH_COMMAND.equals(command[0])){
			createFile(ctx, command[1]);
		}else if(MAKE_DIRECTORY.equals(command[0])){
			Path rootPath = Path.of("cloud", userName, command[1]);
			createDirectory(ctx, rootPath);
		}else if(RM_COMMAND.equals(command[0])){
			removeObj(ctx,command[1]);
		}else if(CHANGE_DIRECTORY.equals(command[0])){
			changingDirectory(ctx, command[1]);
		}else if(DOWNLOAD_COMMAND.equals(command[0])){
			Path srcPath = Path.of(command[1]);
			if(Files.exists(srcPath)){
				if(Files.isDirectory(srcPath)){
					sendDirectory(ctx, srcPath);//пока не могу полностью реализовать, чтобы копировать вместе с содержимым, буду делать через walkFileTree, может получится.
				}else {
					sendFile(ctx, srcPath);
				}
			}
		}
	}

	private void createUserDirectory(ChannelHandlerContext ctx, Path defaultRoot) {
		if (!Files.exists(defaultRoot)) {
			root = Path.of("cloud", userName);
			currentPath = root;
			try {
				Files.createDirectories(root);
			} catch (IOException e) {
				ctx.writeAndFlush(WRONG + "can't create dir");
			}
		}
	}

	private void sendFile(ChannelHandlerContext ctx, Path filename) throws IOException {
		byte[] bytes = Files.readAllBytes(filename);
		ctx.writeAndFlush(DOWNLOAD_COMMAND+" "+ Arrays.toString(bytes));
		System.out.println(DOWNLOAD_COMMAND+ " "+Arrays.toString(bytes));
	}

	private void sendDirectory(ChannelHandlerContext ctx, Path srcPath) {
		//в раздумиях...
	}

	private void changingDirectory(ChannelHandlerContext ctx, String way) {
		if(UP_MARK.equals(way)){
			Path directoryPath = currentPath.getParent();
			if (directoryPath == null || !directoryPath.toString().startsWith("cloud")) {
				return;
			}
			currentPath = Path.of(directoryPath.toString());
			ctx.writeAndFlush(CHANGE_DIRECTORY+" "+ currentPath);

		} else {
			Path directoryPath = Path.of(currentPath.toString(), way);
			if (Files.exists(directoryPath)) {
				currentPath= Path.of(directoryPath.toString());
				ctx.writeAndFlush(CHANGE_DIRECTORY+" "+ currentPath);
			}
		}
	}

	private void removeObj(ChannelHandlerContext ctx, String name) {
		Path newPath = Path.of(currentPath.toString(), name);
		try{
			if(Files.exists(newPath)){
				if(!Files.isDirectory(newPath)){
					Files.delete(newPath);
				} else {
					Files.walkFileTree(newPath, new SimpleFileVisitor<>(){
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							Files.delete(file);
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
							Files.delete(dir);
							return FileVisitResult.CONTINUE;
						}
					});
				}
				ctx.writeAndFlush(RM_COMMAND+" "+ OK);
			}
		} catch (IOException e) {
			ctx.writeAndFlush(RM_COMMAND+ " "+WRONG+" "+ newPath.getFileName()+" can't delete");
		}
	}

	private void createFile(ChannelHandlerContext ctx, String filename) throws IOException {
		Path newFilePath = Path.of(currentPath.toString(), filename);
		if(!Files.exists(newFilePath)){
			Files.createFile(newFilePath);
			ctx.writeAndFlush(TOUCH_COMMAND + " "+OK);
		}else {
			ctx.writeAndFlush(WRONG+ " this name is already used");
		}
	}

	private void createDirectory(ChannelHandlerContext ctx, Path rootPath) throws IOException {
		Path newFilePath = Path.of(currentPath.toString(), rootPath.toString());
		if(!Files.exists(newFilePath)){
			Files.createDirectory(newFilePath);
			ctx.writeAndFlush(MAKE_DIRECTORY + " "+OK);
		}else {
			ctx.writeAndFlush(WRONG+ " this name is already used");
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Client disconnected: " + ctx.channel());
	}
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		//будет просаться эксепшн и закрываться канал при неудачном логировании. Скорее всего...
	}

}
