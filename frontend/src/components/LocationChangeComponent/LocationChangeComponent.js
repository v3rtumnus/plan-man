import React, {useEffect} from "react";

import jwtDecode from "jwt-decode";
import useNotification from "../../hooks/useNotification";

const LocationChangeComponent = ({history}) => {
    const {removeNotification} = useNotification()

    useEffect(
        () => {
            removeNotification();
            if (localStorage.getItem("currentUser")) {
                const decodedToken = jwtDecode(JSON.parse(localStorage.getItem("currentUser")).auth_token);
                console.log(decodedToken.exp * 1000);
                console.log(Date.now());
                if (decodedToken.exp * 1000 < Date.now()) {
                    localStorage.clear();
                    history.push('/login');
                }
            }
        },
        [history, removeNotification]
    )

    return <div></div>
};

export default LocationChangeComponent;