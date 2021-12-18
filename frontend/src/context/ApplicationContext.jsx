import React, {useContext, useEffect, useReducer} from "react";
import {retrieveExpenseCategories} from "../service/ExpenseService";
import useNotification from "../hooks/useNotification";
import {AuthorizationContext} from "./AuthorizationContext";

const initialState = {
  expenseCategories: []
};

export const ApplicationContext = React.createContext(initialState);

export const actionTypes = {
  FETCH_EXPENSE_CATEGORIES: "FETCH_EXPENSE_CATEGORIES",
}


const useActions = (dispatch, addNotification) => {
  return {
    loadExpenseCategories: async () => {
      try {
        const data = await retrieveExpenseCategories();

        dispatch({ type: actionTypes.FETCH_EXPENSE_CATEGORIES, payload: data });
      } catch (error) {
        console.error(error);
        addNotification('Error on loading expense categories', 'ERROR')
      }
    }
  };
};

const reducer = (initialState, action) => {
  switch (action.type) {
    case actionTypes.FETCH_EXPENSE_CATEGORIES:
      return {
        ...initialState,
        expenseCategories: action.payload,
        initialized: true
      };
    default:
      return {
        ...initialState
      }
  }
}

const ApplicationContextProvider = ({ children }) => {
  const {addNotification} = useNotification();
  const {authorizationData} = useContext(AuthorizationContext)
  const [applicationData, dispatch] = useReducer(reducer, initialState);
  const actions = useActions(dispatch, addNotification);

  const {expenseCategories} = applicationData;

  useEffect(() => {
    if (authorizationData.token) {
      actions.loadExpenseCategories();
    }// eslint-disable-next-line (ignore dependency warning)
  }, [authorizationData.token])

  return (<ApplicationContext.Provider value={{expenseCategories}}>
    {children}
  </ApplicationContext.Provider>
  );

}

export default ApplicationContextProvider;