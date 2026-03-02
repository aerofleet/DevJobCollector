import React from 'react';
import '../styles/LoginPage.css';

const LoginPage = () => (
  <div className="login-page">
    <div className="login_input_wrap">
      <div className="login-form-container">
        <h2 className="login_title">로그인</h2>
        <p className="login_subtitle">
          DevJobCollector에 가입한 계정으로 빠르게 채용 정보를 확인해보세요.
        </p>

        <form className="login-form">
          <div className="id-input-box">
            <label className="sr-only" htmlFor="email">
              이메일
            </label>
            <input
              type="email"
              id="email"
              name="email"
              placeholder="이메일 주소"
              autoComplete="username"
              required
            />
          </div>

          <div className="pw-input-box">
            <label className="sr-only" htmlFor="password">
              비밀번호
            </label>
            <input
              type="password"
              id="password"
              name="password"
              placeholder="비밀번호"
              autoComplete="current-password"
              required
            />
          </div>

          <div className="setting">
            <div className="InpBox">
              <input type="checkbox" id="autologin" name="autologin" />
              <label htmlFor="autologin">로그인 유지</label>
            </div>
            <div className="InpBox">
              <input type="checkbox" id="id_save" name="id_save" />
              <label htmlFor="id_save">아이디 저장</label>
            </div>
          </div>

          <button type="submit" className="btn_login">
            로그인
          </button>
        </form>

        <div className="signup-forgotten">
          <a href="/find-id">아이디 찾기</a>
          <span className="divider">|</span>
          <a href="/find-pw">비밀번호 찾기</a>
          <span className="divider">|</span>
          <a href="/signup" className="emphasize">
            회원가입
          </a>
        </div>
        <div className="social_login_list ">
          <a className="social_icon google" title="google" href="#"></a>
          <a className="social_icon kakao" title="kakao" href="#"></a>
          <a className="social_icon naver" title="naver" href="#"></a>
          <a className="social_icon github" title="github" href="#"></a>
        </div>
      </div>
    </div>
  </div>
);

export default LoginPage;
