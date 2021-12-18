import React, { useReducer } from "react";
import {loginUser} from "../service/AuthorizationService";
import useNotification from "../hooks/useNotification";

let user = localStorage.getItem("currentUser")
    ? JSON.parse(localStorage.getItem("currentUser")).user
    : "";
let token = localStorage.getItem("currentUser")
    ? JSON.parse(localStorage.getItem("currentUser")).auth_token
    : "";

const initialState = {
  user: "" || user,
  token: "" || token
};

export const AuthorizationContext = React.createContext(initialState);

export const actionTypes = {
  LOGIN_SUCCESS: "LOGIN_SUCCESS",
  LOGOUT: "LOGOUT"
}


const useActions = (dispatch, addNotification) => {
  return {
    authenticate: async (username, password) => {
      try {
        const data = await loginUser(username, password);

        if (data && data.user) {
          dispatch({ type: 'LOGIN_SUCCESS', payload: data });
        } else {
          addNotification('Error on login', 'ERROR')
        }

        return data;
      } catch (error) {
        console.error(error);
        addNotification('Error on login', 'ERROR')
      }
    },
    logout() {
      dispatch({ type: 'LOGOUT' });
      localStorage.removeItem('currentUser');
    }
  };
};

const reducer = (initialState, action) => {
  switch (action.type) {
    case "LOGIN_SUCCESS":
      return {
        user: action.payload.user,
        token: action.payload.auth_token
      };
    case "LOGOUT":
      return {
        user: "",
        token: ""
      };
    default:
      return {
        ...initialState
      }
  }
}

const AuthorizationContextProvider = ({ children }) => {
  const {addNotification} = useNotification();
  const [authorizationData, dispatch] = useReducer(reducer, initialState);
  const authActions = useActions(dispatch, addNotification);

  return (<AuthorizationContext.Provider value={{authActions, authorizationData}}>
    {children}
  </AuthorizationContext.Provider>
  );

}

export default AuthorizationContextProvider;