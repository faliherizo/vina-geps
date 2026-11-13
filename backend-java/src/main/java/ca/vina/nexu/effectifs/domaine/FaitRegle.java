package ca.vina.nexu.effectifs.domaine;

/** Un fait qu'un contexte expose au moteur de règles : ce qu'une condition peut lire. */
public record FaitRegle(String code, TypeFaitRegle type, String libelle) { }
