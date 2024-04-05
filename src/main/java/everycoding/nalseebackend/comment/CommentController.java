package everycoding.nalseebackend.comment;

import everycoding.nalseebackend.api.ApiResponse;
import everycoding.nalseebackend.auth.customUser.CustomUserDetails;
import everycoding.nalseebackend.auth.jwt.JwtTokenProvider;
import everycoding.nalseebackend.comment.dto.CommentRequestDto;
import everycoding.nalseebackend.comment.dto.CommentResponseDto;
import everycoding.nalseebackend.firebase.FcmService;
import everycoding.nalseebackend.firebase.alarm.AlarmRepository;
import everycoding.nalseebackend.firebase.alarm.domain.Alarm;
import everycoding.nalseebackend.firebase.dto.FcmSendDto;
import everycoding.nalseebackend.user.UserRepository;
import everycoding.nalseebackend.user.UserService;
import everycoding.nalseebackend.user.domain.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final FcmService fcmService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AlarmRepository alarmRepository;

    // 댓글 조회
    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<List<CommentResponseDto>> getComments(@PathVariable Long postId) {
        return ApiResponse.ok(commentService.getComments(postId));
    }

    // 댓글 작성
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<Void> writeComment(
            @PathVariable Long postId,
            @RequestBody CommentRequestDto requestDto,
            HttpServletRequest request
    ) throws IOException {
        String token = "";
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("AccessToken")) {
                token = cookie.getValue();
            }
        }
         Claims claims = jwtTokenProvider.getClaims(token);
         String userEmail = claims.getSubject();
         Optional<User> byEmail = userRepository.findByEmail(userEmail);
         User user = byEmail.orElseThrow();
         String username = user.getUsername();

         String userToken = userService.findUserTokenByPostId(postId);
        User userByPostId = userService.findUserByPostId(postId);
         if(userToken != null && !userToken.equals("error")) {
             String message = username + "님께서 댓글을 작성했습니다.";
             //  FCM 메시지 생성 및 전송
             FcmSendDto fcmSendDto = FcmSendDto.builder()
                     .token(userToken)
                     .title("새로운 댓글 알림")
                     .body(message)
                     .postId(postId)
                     .build();

             fcmService.sendMessageTo(fcmSendDto);

             Alarm alarm = Alarm.builder()
                     .senderId(user.getId())
                     .senderImg(user.getPicture())
                     .senderName(username)
                     .user(userByPostId)
                     .message(message)
                     .build();

             alarmRepository.save(alarm);
         }

        commentService.writeComment(postId, requestDto);
        return ApiResponse.ok();
    }

    // 댓글 수정
    @PatchMapping("/api/posts/{postId}/comments/{commentId}")
    public ApiResponse<Void> updateComment(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody CommentRequestDto requestDto
    ) {
        commentService.updateComment(customUserDetails.getId(), postId, commentId, requestDto);
        return ApiResponse.ok();
    }

    // 댓글 삭제
    @DeleteMapping("/api/posts/{postId}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(customUserDetails.getId(), postId, commentId);
        return ApiResponse.ok();
    }

    // 댓글 좋아요
    @PostMapping("/api/posts/{postId}/comment/{commentId}/likes")
    public ApiResponse<Void> likeComment(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            HttpServletRequest request
            ) throws IOException {
        String token = "";
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("AccessToken")) {
                token = cookie.getValue();
            }
        }
        Claims claims = jwtTokenProvider.getClaims(token);
        String userEmail = claims.getSubject();
        Optional<User> byEmail = userRepository.findByEmail(userEmail);
        User user = byEmail.orElseThrow();
        String username = user.getUsername();

        String userToken = userService.findUserTokenByCommentId(commentId);
        User userByCommentId = userService.findUserByCommentId(commentId);

        if(userToken != null && !userToken.equals("error")) {
            String message = username +"님이 댓글에 좋아요를 눌렀습니다.";
            //  FCM 메시지 생성 및 전송
            FcmSendDto fcmSendDto = FcmSendDto.builder()
                    .token(userToken)
                    .title("좋아요 알림")
                    .body(message)
                    .commentId(commentId)
                    .build();

            fcmService.sendMessageTo(fcmSendDto);

            Alarm alarm = Alarm.builder()
                    .senderId(user.getId())
                    .senderImg(user.getPicture())
                    .senderName(username)
                    .user(userByCommentId)
                    .message(message)
                    .build();

            alarmRepository.save(alarm);
        }
        commentService.likeComment(customUserDetails.getId(), postId, commentId);
        return ApiResponse.ok();
    }

    // 댓글 좋아요 취소
    @PostMapping("/api/posts/{postId}/comment/{commentId}/likes/cancel")
    public ApiResponse<Void> cancelLikeComment(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        commentService.cancelLikeComment(customUserDetails.getId(), postId, commentId);
        return ApiResponse.ok();
    }
}
