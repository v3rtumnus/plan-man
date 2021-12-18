import React, {useContext} from 'react'
import {AuthorizationContext} from "../../context/AuthorizationContext";

function Dashboard(props) {
    const { authorizationData, authActions } = useContext(AuthorizationContext)
 
    const handleLogout = () => {
        authActions.logout()
        
        props.history.push('/login')
    }
    return (
        <div style={{ padding: 10 }}>
            <div >
                <h1>
                    Dashboard
                </h1>
                <button onClick={handleLogout}>Logout</button>
            </div>
            <p>Welcome {authorizationData.user}</p>
        </div>
    )
}

export default Dashboard
