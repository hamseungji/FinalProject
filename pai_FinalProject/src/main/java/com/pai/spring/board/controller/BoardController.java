package com.pai.spring.board.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.pai.spring.board.model.service.BoardService;
import com.pai.spring.board.model.vo.AttachFile;
import com.pai.spring.board.model.vo.Board;
import com.pai.spring.board.model.vo.BoardComment;
import com.pai.spring.board.model.vo.BoardLike;
import com.pai.spring.common.PageFactory;
import com.pai.spring.member.model.vo.Member;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/board")
@Slf4j
public class BoardController {

	@Autowired
	private BoardService service;
	
	@RequestMapping("/boardList.do")
	public ModelAndView BoardList(ModelAndView mv,@RequestParam(value="cPage",defaultValue="1") int cPage, @RequestParam(value="numPerPage",defaultValue="10") int numPerPage) {
		List<Board> list=service.boardList(cPage,numPerPage); 
		List<Board> readList=service.readList(); 
		List<Board> likeList=service.likeList();
		int totalDate=service.selectBoardCount();
		 
		mv.addObject("readList", readList);
		mv.addObject("likeList", likeList);
		mv.addObject("pageBar",PageFactory.getPageBar(totalDate, cPage, numPerPage, 5, "boardList.do",""));
		mv.addObject("list", list); 
	
		return mv;
	}
	
	@RequestMapping("/insertBoard.do")
	public String insertBoard() { 
		return "board/insertBoard";
	}
	
	@RequestMapping("/searchBoard.do")
	public ModelAndView searchBoard(ModelAndView mv,@RequestParam(value="category") String category
										,@RequestParam(value="searchType") String searchType,@RequestParam(value="keyword") String keyword
										,@RequestParam(value="cPage",defaultValue="1") int cPage, @RequestParam(value="numPerPage",defaultValue="10") int numPerPage) {
		
		//System.out.println(category);
		//System.out.println(searchType);
		//System.out.println(keyword);
		Map<String,Object> param=new HashMap();
		param.put("category", category);
		param.put("keyword", keyword);
		param.put("searchType", searchType);
		param.put("cPage", cPage);
		param.put("numPerPage", numPerPage);
		List<Board> list=service.searchBoard(param,cPage,numPerPage);
		int totalDate=service.searchBoardCount(param);
		
		List<Board> readList=service.readList(); 
		List<Board> likeList=service.likeList(); 
		mv.addObject("category", category); 
		mv.addObject("searchType", searchType); 
		mv.addObject("keyword", keyword); 
		mv.addObject("readList", readList);
		mv.addObject("likeList", likeList);
		mv.addObject("pageBar", PageFactory.getPageBar(totalDate, cPage, numPerPage, 5, "searchBoard.do","&category="+category+"&searchType="+searchType+"&keyword="+keyword));
		mv.addObject("list", list);
		mv.setViewName("board/boardList");
		return mv;
	}
	
	@RequestMapping("/boardView.do")
	public ModelAndView boardView(ModelAndView mv,String memberId,@RequestParam(value="boardNo") int boardNo,HttpServletRequest req,HttpServletResponse res) {

		Cookie[] cookies=req.getCookies();
		String boardRead=""; 
		boolean isRead=false; // 읽었으면 true, 안읽었으면 false 
		
		if(cookies!=null) {
			for(Cookie c : cookies) {
				String name=c.getName();
				String value=c.getValue();
				if(name.equals("boardRead")) {
					boardRead=value;
					if(value.contains("|"+boardNo+"|")) {
						isRead=true;
						break;
					}
				}
			}
		}
		
		if(!isRead) {
			Cookie c = new Cookie("boardRead",boardRead+"|"+boardNo+"|");
			c.setMaxAge(24*60*60);
			res.addCookie(c);
		}
		
		
		Board b=service.selectBoard(boardNo,isRead);  
		List<BoardComment> list=service.boardCommentList(boardNo);
		Map param=Map.of("memberId",memberId,"boardNo",boardNo);
		BoardLike like = service.selectBoardLike(param);
		mv.addObject("like",like);
		mv.addObject("board", b); 
		mv.addObject("comments", list);
		mv.addObject("commentCount", b.getComment().size());
		return mv;
	}
	
	@RequestMapping("/ajax/boardView.do")
	@ResponseBody
	public Boolean selectBoardView(@RequestParam(value="boardNo") int boardNo,HttpServletRequest req,HttpServletResponse res) {
		Cookie[] cookies=req.getCookies();
		String boardRead=""; 
		boolean isRead=false; // 읽었으면 true, 안읽었으면 false 
		
		if(cookies!=null) {
			for(Cookie c : cookies) {
				String name=c.getName();
				String value=c.getValue();
				if(name.equals("boardRead")) {
					boardRead=value;
					if(value.contains("|"+boardNo+"|")) {
						isRead=true;
						break;
					}
				}
			}
		}
		
		if(!isRead) {
			Cookie c = new Cookie("boardRead",boardRead+"|"+boardNo+"|");
			c.setMaxAge(24*60*60);
			res.addCookie(c);
		}
		
		Board b=service.selectBoard(boardNo,isRead);
		//System.out.println(b);
		return b==null;
	}
	
	
	@RequestMapping("/insertBoardEnd.do")
	@ResponseBody
	public Boolean insertBoardEnd(@RequestParam(value="upFile",required=false) MultipartFile[] upFile,
									@RequestParam(value="boardTitle") String boardTitle, @RequestParam(value="boardCategory") String boardCategory,
									@RequestParam(value="boardContent") String boardContent,@RequestParam(value="memberId") String memberId,HttpServletRequest req) {
		 
		String path=req.getServletContext().getRealPath("/resources/upload/board/");
		File file=new File(path);
		if(!file.exists()) file.mkdirs();
		for(MultipartFile mf : upFile) {
			if(!mf.isEmpty()) {
				try {
					mf.transferTo(new File(path+mf.getOriginalFilename()));
				}catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		Member m=Member.builder().member_id(memberId).build();
		List<AttachFile> filenames=new ArrayList();
		AttachFile f=null;
		for(int i=0;i<upFile.length;i++) {
			f=AttachFile.builder().attachFileName(upFile[i].getOriginalFilename()).build();
			filenames.add(f); 
		}
		
		Board b=Board.builder().boardTitle(boardTitle).boardCategory(boardCategory).boardContent(boardContent).boardWriter(m).attachFile(filenames).build();
		int result=service.insertBoard(b);
		return result>0;
	}
	
	@RequestMapping("/boardUpdate.do")
	public ModelAndView boardUpdate(ModelAndView mv,int boardNo) { 
		Board b=service.selectBoard(boardNo); 
		mv.addObject("board", b);
		return mv;
	}
	
	@RequestMapping("/updateBoardEnd.do")
	@ResponseBody
	public Boolean updateBoardEnd(@RequestParam(value="upFile",required=false) MultipartFile[] upFile,
			@RequestParam(value="boardTitle") String boardTitle, @RequestParam(value="boardCategory") String boardCategory,@RequestParam(value="boardNo") int boardNo,
			@RequestParam(value="boardContent") String boardContent,@RequestParam(value="memberId") String memberId,HttpServletRequest req) {
		String path=req.getServletContext().getRealPath("/resources/upload/board/");
		File file=new File(path);
		if(!file.exists()) file.mkdirs();
		for(MultipartFile mf : upFile) {
			if(!mf.isEmpty()) {
				try {
					mf.transferTo(new File(path+mf.getOriginalFilename()));
				}catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		Member m=Member.builder().member_id(memberId).build();
		List<AttachFile> filenames=new ArrayList();
		AttachFile f=null;
		for(int i=0;i<upFile.length;i++) {
			f=AttachFile.builder().attachFileName(upFile[i].getOriginalFilename()).build();
			filenames.add(f); 
		}
		
		Board b=Board.builder().boardNo(boardNo).boardTitle(boardTitle).boardCategory(boardCategory).boardContent(boardContent).boardWriter(m).attachFile(filenames).build();
		//System.out.println(boardNo+" : "+boardTitle + " : " +boardCategory + " : " + boardContent + " : " + memberId + " : "+ upFile[0].getOriginalFilename());
		
		int result=service.updateBoard(b); 
		return result>0;
	}
	
	
/*	@RequestMapping("/insertBoardComment.do")
	public ModelAndView insertComment(ModelAndView mv,@RequestParam(value="commentWriter") String commentWriter,
									@RequestParam(value="commentContent") String content,@RequestParam(value="commentLevel") int level,
									@RequestParam(value="boardRef") int boardRef, @RequestParam(value="commentRef") int commentRef) {
		 Member m=Member.builder().member_id(commentWriter).build();
		 BoardComment bc = BoardComment.builder().commentLevel(level).commentWriter(m).commentContent(content).boardRef(boardRef).commentRef(commentRef).build();
		 int result=service.insertComment(bc);
		 String msg="";
		 String loc="";
		 if(result>0) {
			 msg="댓글 등록 성공";
			 loc="/board/boardView.do?boardNo="+boardRef;		 
		 }else {
			 msg="댓글 등록 실패";
			 loc="/board/boardView.do?boardNo="+boardRef;
		 }
		 mv.addObject("msg", msg);
		 mv.addObject("loc", loc);
		 mv.setViewName("common/msg");
		return mv;
	} */
	
	@RequestMapping("/insertBoardComment.do")
	public ModelAndView insertComment(ModelAndView mv,BoardComment bc) {
		  System.out.println(bc);
		 //BoardComment bc = BoardComment.builder().commentLevel(level).commentWriter(m).commentContent(content).boardRef(boardRef).commentRef(commentRef).build();
		 int result=service.insertComment(bc);
		 String msg="";
		 String loc="";
		 if(result>0) {
			 msg="댓글 등록 성공";
			 loc="/board/boardView.do?boardNo="+bc.getBoardRef();		 
		 }else {
			 msg="댓글 등록 실패";
			 loc="/board/boardView.do?boardNo="+bc.getBoardRef();
		 }
		 mv.addObject("msg", msg);
		 mv.addObject("loc", loc);
		 mv.setViewName("common/msg");
		return mv;
	}
	
	@RequestMapping("/insertBoardComment2.do")
	public ModelAndView insertComment2(ModelAndView mv,BoardComment bc) {
		   
		 int result=service.insertComment2(bc);
		 String msg="";
		 String loc="";
		 if(result>0) {
			 msg="댓글 등록 성공";
			 loc="/board/boardView.do?boardNo="+bc.getBoardRef();		 
		 }else {
			 msg="댓글 등록 실패";
			 loc="/board/boardView.do?boardNo="+bc.getBoardRef();
		 }
		 mv.addObject("msg", msg);
		 mv.addObject("loc", loc);
		 mv.setViewName("common/msg");
		return mv;
	}
	
	@RequestMapping("/boardDelete.do")
	public ModelAndView deleteBoard(ModelAndView mv,int boardNo) {
		 int result=service.deleteBoard(boardNo);
		
		 String msg="";
		 String loc="";
		 if(result>0) {
			 msg="게시물이 성공적으로 삭제되었습니다!";
			 loc="/board/boardList.do";		 
		 }else {
			 msg="게시물 삭제에 실패했습니다";
			 loc="/board/boardList.do";	
		 }
		 mv.addObject("msg", msg);
		 mv.addObject("loc", loc);
		 mv.setViewName("common/msg"); 
		return mv;
	}
	
	@RequestMapping("/commentDelete.do")
	public ModelAndView commentDelete(ModelAndView mv,int commentNo,int boardNo) {
		 int result=service.commentDelete(commentNo);
		
		 String msg="";
		 String loc="";
		 if(result>0) {
			 msg="댓글이 성공적으로 삭제되었습니다!";
			 loc="/board/boardView.do?boardNo="+boardNo;	 
		 }else {
			 msg="댓글삭제에 실패했습니다";
			 loc="/board/boardView.do?boardNo="+boardNo;
		 }
		 mv.addObject("msg", msg);
		 mv.addObject("loc", loc);
		 mv.setViewName("common/msg"); 
		return mv;
	}
	
	@RequestMapping("/boardLike.do") 
	public ModelAndView boardLike(String memberId,int boardNo,ModelAndView mv) {
		System.out.println(memberId+" : "+boardNo);
		//Member m=service.selectMember(memberId);
		//Board b=service.selectBoard(boardNo);
		//BoardLike like=BoardLike.builder().boardNo(b).memberId(m).build(); 
		//1. board_like테이블에 데이터값 유무 확인
		Map param=Map.of("memberId",memberId,"boardNo",boardNo);
		BoardLike like = service.selectBoardLike(param);
		//2. 있으면 삭제 , 없으면 추가하는 로직  
		int result;
		if(like==null) {
			result=service.insertBoardLike(param);
		}else {
			result=service.deleteBoardLike(param);
		}
		//3. 결과 응답
		System.out.println(like);
		Board b=service.selectBoard(boardNo);  
		List<BoardComment> list=service.boardCommentList(boardNo); 
		mv.addObject("board", b); 
		mv.addObject("comments", list);
		mv.addObject("commentCount", b.getComment().size());
		BoardLike like2 = service.selectBoardLike(param);
		mv.addObject("like",like2);
		mv.setViewName("board/boardView");
		return mv;
	}
	
	@RequestMapping("/clickRead.do")
	public ModelAndView clickRead(String select,ModelAndView mv,@RequestParam(value="cPage",defaultValue="1") int cPage, @RequestParam(value="numPerPage",defaultValue="10") int numPerPage) {
		 
		List<Board> list=service.boardReadList(cPage,numPerPage); 
		List<Board> readList=service.readList(); 
		List<Board> likeList=service.likeList();
		int totalDate=service.selectBoardCount();
		mv.addObject("select", select); 
		mv.addObject("readList", readList);
		mv.addObject("likeList", likeList);
		mv.addObject("pageBar",PageFactory.getPageBar(totalDate, cPage, numPerPage, 5, "boardList.do",""));
		mv.addObject("list", list); 
		mv.setViewName("board/boardList");
		return mv;
	}
	
	@RequestMapping("/clickLike.do")
	public ModelAndView clickLike(String select,ModelAndView mv,@RequestParam(value="cPage",defaultValue="1") int cPage, @RequestParam(value="numPerPage",defaultValue="10") int numPerPage) {
		List<Board> list=service.boardLikeList(cPage,numPerPage); 
		List<Board> readList=service.readList(); 
		List<Board> likeList=service.likeList();
		int totalDate=service.selectBoardCount();
		mv.addObject("select", select); 
		mv.addObject("readList", readList);
		mv.addObject("likeList", likeList);
		mv.addObject("pageBar",PageFactory.getPageBar(totalDate, cPage, numPerPage, 5, "boardList.do",""));
		mv.addObject("list", list); 
		mv.setViewName("board/boardList");
		return mv;
	}
	
	@RequestMapping("/insertDeclare.do")
	public ModelAndView insertDeclare(ModelAndView mv,String declare,String memberId,int boardNo) {
		System.out.println(declare + " : " +memberId+" : "+boardNo);
		return mv;
	}
}
