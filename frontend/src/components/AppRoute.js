import React, {useContext} from "react";
import { Redirect, Route } from "react-router-dom";

import {AuthorizationContext} from "../context/AuthorizationContext";
 
const AppRoute = ({ component: Component, path, isPrivate, ...rest }) => {

    const { authorizationData } = useContext(AuthorizationContext)
    return (
        <Route
            path={path}
            render={props =>
                isPrivate && !Boolean(authorizationData.token) ? (
                    <Redirect
                        to={{ pathname: "/login" }}
                    />
                ) : (
                        <Component {...props} />
                    )
            }
            {...rest}
        />
    )
}
 
export default AppRoute
