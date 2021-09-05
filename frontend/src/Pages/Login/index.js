
import { React, useState } from 'react';

import styles from './login.module.css';
import { loginUser, useAuthState, useAuthDispatch } from '../../context'

function Login(props) {
 
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
 
    const dispatch = useAuthDispatch()
    const { loading, errorMessage } = useAuthState() //read the values of loading and errorMessage from context
 
 
 
 
    const handleLogin = async (e) => {
        e.preventDefault()
 
        try {
            let response = await loginUser(dispatch, { username, password })
            if (!response.user) return
            props.history.push('/dashboard')
        } catch (error) {
            console.log(error)
        }
    }
 
    return (
        <div className={styles.container}>
            <div className={{ width: 200 }}>
                <h1>Login Page</h1>
                {
                    errorMessage ? <p className={styles.error}>{errorMessage}</p> : null
                }
                <form >
                    <div className={styles.loginForm}>
                        <div className={styles.loginFormItem}>
                            <label htmlFor="username">Username</label>
                            <input type="text" id='username' value={username} onChange={(e) => setUsername(e.target.value)} disabled={loading} />
                        </div>
                        <div className={styles.loginFormItem}>
                            <label htmlFor="password">Password</label>
                            <input type="password" id='password' value={password} onChange={(e) => setPassword(e.target.value)} disabled={loading} />
                        </div>
                    </div>
                    <button onClick={handleLogin} disabled={loading}>login</button>
                </form>
            </div>
        </div>
    )
}

export default Login;
